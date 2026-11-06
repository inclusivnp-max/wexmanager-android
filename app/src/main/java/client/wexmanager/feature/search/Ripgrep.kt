package client.wexmanager.feature.search

import android.content.Context
import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/**
 * Поиск по содержимому через ripgrep (Rust), как в WEXdesktop: `rg --json`, вывод разбирается потоком.
 * Бинарник лежит в APK как `librg.so` (см. tools/build-ripgrep.sh): установщик распаковывает его
 * в nativeLibraryDir — оттуда приложению разрешено запускать исполняемые файлы.
 */
object Ripgrep {
    @Volatile private var binary: File? = null

    fun init(context: Context) {
        binary = File(context.applicationInfo.nativeLibraryDir ?: return, "librg.so").takeIf { it.isFile && it.canExecute() }
    }

    /** Для отладочных замеров вне приложения (app_process): путь к librg.so или null — выключить. */
    fun useBinary(file: File?) {
        binary = file?.takeIf { it.isFile && it.canExecute() }
    }

    val available: Boolean get() = binary != null

    /** Совпадения одного файла: первая строка для превью и число совпавших строк. */
    class FileMatches(val path: String, val line: Int, val text: String, val ranges: List<IntRange>, val count: Int)

    /** [searched] — сколько файлов rg просмотрел (из итоговой статистики), -1 — неизвестно. */
    class Outcome(val files: Int, val searched: Int, val stopped: Boolean, val error: String?)

    internal fun command(rg: String, roots: List<String>, query: String, regex: Boolean, caseSensitive: Boolean, includeHidden: Boolean): List<String> = buildList {
        add(rg)
        add("--json")
        add("--no-config")
        add("--no-messages")
        add("--no-ignore") // файловый менеджер: .gitignore не прячет файлы
        add("--follow")
        add("--max-filesize"); add("20M")
        add("--max-count"); add("999")
        add(if (caseSensitive) "--case-sensitive" else "--ignore-case")
        if (!regex) add("--fixed-strings")
        if (includeHidden) add("--hidden")
        add("--regexp"); add(query)
        add("--")
        addAll(roots)
    }

    /**
     * Ищет [query] в [roots]; [onFile] получает файлы с совпадениями по мере нахождения (поток IO)
     * и возвращает false, чтобы остановить поиск. Отмена корутины убивает процесс.
     */
    suspend fun search(
        roots: List<String>,
        query: String,
        regex: Boolean,
        caseSensitive: Boolean,
        includeHidden: Boolean,
        onFile: (FileMatches) -> Boolean,
    ): Outcome = withContext(Dispatchers.IO) {
        val rg = binary?.path ?: throw IOException("ripgrep недоступен")
        val process = ProcessBuilder(command(rg, roots, query, regex, caseSensitive, includeHidden))
            .apply { environment().remove("RIPGREP_CONFIG_PATH") }
            .start()
        val kill = currentCoroutineContext().job.invokeOnCompletion { process.destroy() }
        process.outputStream.close()
        val stderr = ByteArrayOutputStream()
        val errReader = Thread({
            runCatching {
                process.errorStream.use { s ->
                    val buf = ByteArray(4096)
                    while (true) {
                        val n = s.read(buf)
                        if (n < 0) break
                        if (stderr.size() < 16_000) stderr.write(buf, 0, n)
                    }
                }
            }
        }, "rg-stderr").apply { isDaemon = true; start() }

        var files = 0
        var searched = -1
        var stopped = false
        var current: String? = null
        var first: FileMatches? = null
        var count = 0
        try {
            // Потоком, а не строками: строка минифицированного файла — JSON в десятки мегабайт.
            JsonReader(process.inputStream.bufferedReader(Charsets.UTF_8)).use { r ->
                r.isLenient = true
                while (r.peek() != JsonToken.END_DOCUMENT) {
                    ensureActive()
                    when (val m = readMessage(r)) {
                        is Msg.Begin -> { current = m.path; first = null; count = 0 }
                        is Msg.Match -> {
                            count++
                            if (first == null) first = m.found
                        }
                        is Msg.End -> {
                            val f = first
                            if (f != null && m.path == current) {
                                files++
                                if (!onFile(FileMatches(f.path, f.line, f.text, f.ranges, count))) {
                                    stopped = true
                                    break
                                }
                            }
                            current = null
                        }
                        is Msg.Summary -> searched = m.searches
                        null -> Unit
                    }
                }
            }
        } catch (_: IOException) {
            // процесс убит отменой или поток оборван
        } catch (_: IllegalStateException) {
            // неожиданная структура вывода: что успели — показываем
        } finally {
            if (stopped) process.destroy()
            kill.dispose()
        }
        val code = runCatching { process.waitFor() }.getOrDefault(-1)
        errReader.join(1000)
        val err = stderr.toString("UTF-8").trim()
        // 1 — ничего не нашлось; 2 — ошибка (например, в регулярном выражении).
        val error = if (code == 2 && err.isNotEmpty() && files == 0) err.lines().filter { it.isNotBlank() }.joinToString("\n") { it.removePrefix("rg: ") }.take(600) else null
        Outcome(files, searched, stopped, error)
    }

    private sealed interface Msg {
        class Begin(val path: String) : Msg
        class Match(val found: FileMatches) : Msg
        class End(val path: String) : Msg
        class Summary(val searches: Int) : Msg
    }

    /** Одно сообщение rg --json; у begin/match/end «type» идёт раньше «data». */
    private fun readMessage(r: JsonReader): Msg? {
        var type: String? = null
        var result: Msg? = null
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "type" -> type = r.nextString()
                "data" -> result = when (type) {
                    "begin", "end" -> readPathOnly(r)?.let { if (type == "begin") Msg.Begin(it) else Msg.End(it) }
                    "match" -> readMatch(r)?.let { Msg.Match(it) }
                    // У итогового сообщения ключи по алфавиту: «data» раньше «type».
                    "summary", null -> Msg.Summary(readSearches(r))
                    else -> { r.skipValue(); null }
                }
                else -> r.skipValue()
            }
        }
        r.endObject()
        return if (result is Msg.Summary && type != "summary") null else result
    }

    /** data.stats.searches итогового сообщения. */
    private fun readSearches(r: JsonReader): Int {
        var n = -1
        r.beginObject()
        while (r.hasNext()) {
            if (r.nextName() == "stats") {
                r.beginObject()
                while (r.hasNext()) if (r.nextName() == "searches") n = r.nextInt() else r.skipValue()
                r.endObject()
            } else r.skipValue()
        }
        r.endObject()
        return n
    }

    private fun readPathOnly(r: JsonReader): String? {
        var path: String? = null
        r.beginObject()
        while (r.hasNext()) {
            if (r.nextName() == "path") path = readTextOrBytes(r) else r.skipValue()
        }
        r.endObject()
        return path
    }

    /** {"text": …} или {"bytes": base64} (не UTF-8 имя или строка). */
    private fun readTextOrBytes(r: JsonReader): String? {
        if (r.peek() == JsonToken.NULL) { r.nextNull(); return null }
        var out: String? = null
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "text" -> out = r.nextString()
                "bytes" -> out = runCatching { String(Base64.decode(r.nextString(), Base64.DEFAULT), Charsets.UTF_8) }.getOrNull()
                else -> r.skipValue()
            }
        }
        r.endObject()
        return out
    }

    private const val MAX_SUBMATCHES = 64

    private fun readMatch(r: JsonReader): FileMatches? {
        var path: String? = null
        var lines: String? = null
        var lineNumber = -1
        val subs = ArrayList<IntArray>()
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "path" -> path = readTextOrBytes(r)
                "lines" -> lines = readTextOrBytes(r)
                "line_number" -> if (r.peek() == JsonToken.NULL) r.nextNull() else lineNumber = r.nextInt()
                "submatches" -> {
                    r.beginArray()
                    while (r.hasNext()) {
                        if (subs.size >= MAX_SUBMATCHES) { r.skipValue(); continue }
                        var start = -1
                        var end = -1
                        r.beginObject()
                        while (r.hasNext()) {
                            when (r.nextName()) {
                                "start" -> start = r.nextInt()
                                "end" -> end = r.nextInt()
                                else -> r.skipValue()
                            }
                        }
                        r.endObject()
                        if (start >= 0 && end >= start) subs += intArrayOf(start, end)
                    }
                    r.endArray()
                }
                else -> r.skipValue()
            }
        }
        r.endObject()
        val text = lines ?: return null
        if (path == null || lineNumber < 0) return null
        // Несколько строк бывает только у многострочных выражений — показываем первую.
        val first = text.substringBefore('\n').removeSuffix("\r")
        val ranges = ArrayList<IntRange>()
        val cursor = Utf8Cursor(text)
        for (sm in subs) {
            val start = cursor.charIndex(sm[0])
            if (start >= first.length) break
            val end = cursor.charIndex(sm[1])
            ranges += start until minOf(end, first.length).coerceAtLeast(start + 1)
            if (ranges.size >= 8) break
        }
        return FileMatches(path, lineNumber, first, ranges, 1)
    }

    /** Байтовые смещения UTF-8 (по возрастанию) → индексы символов строки. */
    private class Utf8Cursor(private val s: String) {
        private var bytes = 0
        private var i = 0

        fun charIndex(byteOffset: Int): Int {
            if (byteOffset < bytes) { bytes = 0; i = 0 }
            while (i < s.length && bytes < byteOffset) {
                val c = s[i]
                when {
                    c.code < 0x80 -> bytes += 1
                    c.code < 0x800 -> bytes += 2
                    Character.isHighSurrogate(c) && i + 1 < s.length && Character.isLowSurrogate(s[i + 1]) -> { bytes += 4; i++ }
                    else -> bytes += 3
                }
                i++
            }
            return i
        }
    }
}
