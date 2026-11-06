package client.wexmanager.core.archive

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * RAR5 через UnRAR от RARLAB (tools/build-unrar.sh → nativeLibraryDir/libunrar.so): junrar читает только RAR4.
 *
 * Листинг — технический вывод `lt -v` (все тома многотомного архива); распаковка — `x` во временную папку:
 * UnRAR сам сверяет CRC и пароль и удаляет битые файлы, а на место переносится только проверенное.
 * Пароль всегда передаётся ключом (`-p-` — «не спрашивать»), интерактивных вопросов нет; отмена корутины
 * убивает процесс.
 */
object Unrar {
    @Volatile private var binary: File? = null
    @Volatile private var home: File? = null

    /** Запись архива; [link] — символическая/жёсткая ссылка (не распаковывается). */
    data class Entry(val name: String, val isDir: Boolean, val link: Boolean, val size: Long, val time: Long, val encrypted: Boolean)

    fun init(context: Context) {
        binary = File(context.applicationInfo.nativeLibraryDir, "libunrar.so").takeIf { it.isFile }
        home = context.filesDir
    }

    val available: Boolean get() = binary != null

    private val RAR5 = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00)

    fun isRar5(file: File): Boolean = runCatching {
        file.inputStream().use { s -> val h = ByteArray(8); s.read(h) == 8 && h.contentEquals(RAR5) }
    }.getOrDefault(false)

    /** RAR5 и есть чем его читать. */
    fun handles(file: File): Boolean = available && isRar5(file)

    private fun command(vararg args: String): ProcessBuilder {
        val bin = binary ?: throw IOException("UnRAR недоступен")
        // -cfg- — без .rarrc и переменной RAR; -y — «да» на любые вопросы; -c- — без вывода комментария архива
        // (его строки вида «Name: …» смешались бы с листингом).
        return ProcessBuilder(listOf(bin.path) + args.toList()).apply {
            environment()["LANG"] = "C.UTF-8"
            environment()["LC_ALL"] = "C.UTF-8"
            environment().remove("RAR")
            // Свой HOME: иначе UnRAR ищет .rarrc в чужих папках (отказы SELinux в логе).
            home?.let { environment()["HOME"] = it.path }
        }
    }

    private fun pass(password: String?) = if (password.isNullOrEmpty()) "-p-" else "-p$password"

    /**
     * Запуск с отменой: вывод (stdout + stderr) передаётся кусками в [onChunk] (UnRAR рисует проценты через «\b»),
     * отмена корутины убивает процесс. Возвращает код выхода и вывод.
     */
    private suspend fun run(pb: ProcessBuilder, onChunk: (String) -> Unit = {}): Pair<Int, String> = withContext(Dispatchers.IO) {
        val p = pb.redirectErrorStream(true).start()
        try {
            coroutineScope {
                val reader = async {
                    val all = StringBuilder()
                    val buf = CharArray(8192)
                    p.inputStream.reader(Charsets.UTF_8).use { r ->
                        while (true) {
                            val n = r.read(buf)
                            if (n < 0) break
                            val chunk = String(buf, 0, n)
                            if (all.length < 4_000_000) all.append(chunk)
                            onChunk(chunk)
                        }
                    }
                    all.toString()
                }
                val out = try {
                    reader.await()
                } catch (e: CancellationException) {
                    p.destroyForcibly() // разблокирует чтение — coroutineScope дождётся читателя
                    throw e
                }
                p.waitFor() to out
            }
        } finally {
            if (p.isAlive) p.destroyForcibly()
        }
    }

    /** Коды выхода UnRAR → исключения; 11 — нужен или не подошёл пароль. */
    private fun fail(code: Int, password: String?, output: String): Nothing = when (code) {
        11 -> throw ArchivePasswordException(wrong = !password.isNullOrEmpty())
        8 -> throw IOException("Недостаточно памяти для распаковки RAR")
        10 -> throw IOException("Нет файлов для распаковки")
        3 -> throw IOException("Архив повреждён: " + damaged(output).ifEmpty { listOf("ошибка контрольной суммы") }.joinToString(", "))
        else -> throw IOException("Ошибка RAR (код $code)" + output.lineSequence().map { it.trim() }.lastOrNull { it.isNotEmpty() && !it.endsWith('%') }?.let { ": $it" }.orEmpty())
    }

    /** Имена записей, о которых UnRAR сообщил «checksum error» / «CRC failed». */
    internal fun damaged(output: String): List<String> = output.lineSequence().mapNotNull { line ->
        val l = line.trim()
        Regex("^(?:Checksum error in the encrypted file |CRC failed in the encrypted file |)(.+?)(?: - checksum error| - CRC failed|\\. Corrupt file or wrong password\\.)$")
            .find(l)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    }.distinct().toList()

    suspend fun list(file: File, password: String?): List<Entry> {
        val (code, text) = run(command("lt", "-v", "-c-", "-y", "-cfg-", pass(password), "--", file.path))
        if (code != 0) fail(code, password, text)
        return parseTechnical(text)
    }

    private val timeFormat = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }

    /**
     * Разбор `unrar lt -v`: блоки «Name / Type / Size / Ratio / mtime / Flags», разделённые пустой строкой.
     * Части файла в следующих томах (Ratio «<->», «<--») и служебные записи пропускаются.
     */
    internal fun parseTechnical(text: String): List<Entry> {
        val out = ArrayList<Entry>()
        var fields = HashMap<String, String>()
        fun flush() {
            val name = fields["Name"]
            val ratio = fields["Ratio"]?.trim()
            // Служебные записи (QO — быстрое открытие, CMT, ACL, STM) — не файлы архива.
            if (name != null && fields["Type"] != "Service" && ratio != "<->" && ratio != "<--") {
                val type = fields["Type"].orEmpty()
                val time = fields["mtime"]?.substringBefore(',')?.let { runCatching { timeFormat.get()!!.parse(it)?.time }.getOrNull() } ?: 0L
                out += Entry(
                    name = name.replace('\\', '/').trimEnd('/'),
                    isDir = type == "Directory",
                    link = type != "File" && type != "Directory",
                    size = fields["Size"]?.trim()?.toLongOrNull() ?: 0L,
                    time = time,
                    encrypted = fields["Flags"]?.contains("encrypted") == true,
                )
            }
            fields = HashMap()
        }
        for (raw in text.lineSequence()) {
            val line = raw.trimEnd()
            if (line.isBlank()) { flush(); continue }
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val key = line.substring(0, colon).trim()
            if (key == "Name" && fields.containsKey("Name")) flush()
            fields[key] = line.substring(colon + 1).removePrefix(" ")
        }
        flush()
        return out
    }

    /** Имя нельзя передать списком: UnRAR понимает в нём маски и обрезает хвостовые пробелы и кавычки. */
    private fun unsafeForList(name: String) =
        name.any { it == '*' || it == '?' || it == '"' } || name != name.trim() || "//" in name

    /**
     * Распаковка в [dest] (папка создаётся): [names] — только эти файлы (null — все; имена, которые нельзя
     * передать списком, — тоже все, лишнее вызывающий не переносит). Символические ссылки не создаются (`-ol-`).
     * [onPercent] — общий процент. Возвращает имена повреждённых файлов (их UnRAR удалил);
     * неверный пароль — [ArchivePasswordException].
     */
    suspend fun extract(file: File, password: String?, names: List<String>?, dest: File, onPercent: (Int) -> Unit): List<String> {
        dest.mkdirs()
        val args = mutableListOf("x", "-y", "-o+", "-c-", "-cfg-", "-ol-", "-idc", pass(password))
        var listFile: File? = null
        if (names != null && names.none(::unsafeForList)) {
            // `-scfl` — список в UTF-8 («u» было бы UTF-16). Файл списка — вне папки распаковки.
            listFile = File(dest.parentFile, ".wex-unrar-${System.nanoTime()}.lst").apply { writeText(names.joinToString("\n")) }
            args += listOf("-scfl", "-n@${listFile.path}")
        }
        args += listOf("--", file.path, dest.path + "/")
        val percent = Regex("(\\d{1,3})%")
        try {
            val (code, out) = run(command(*args.toTypedArray())) { chunk ->
                percent.findAll(chunk).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()?.let { if (it in 0..100) onPercent(it) }
            }
            return when (code) {
                0 -> emptyList()
                3 -> damaged(out).ifEmpty { throw IOException("Архив повреждён: ошибка контрольной суммы") }
                else -> fail(code, password, out)
            }
        } finally {
            listFile?.delete()
        }
    }

    /** Проверка архива: `t`; возвращает «имя: причина» для повреждённых записей. */
    suspend fun test(file: File, password: String?): List<String> {
        val (code, text) = run(command("t", "-y", "-c-", "-cfg-", pass(password), "--", file.path))
        return when (code) {
            0 -> emptyList()
            3 -> damaged(text).ifEmpty { listOf(file.name) }.map { "$it: данные повреждены (контрольная сумма не совпадает)" }
            else -> fail(code, password, text)
        }
    }
}
