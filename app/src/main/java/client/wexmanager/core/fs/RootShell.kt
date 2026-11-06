package client.wexmanager.core.fs

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

/**
 * Root-доступ через `su` (Magisk, KernelSU…) — только чтение: просмотр закрытых папок,
 * копирование файлов оттуда. Каждая команда — отдельный `su -c`, менеджер root кеширует разрешение.
 */
object RootShell {
    /** Зеркало настройки «Root-доступ» (меняется из AppContainer). */
    @Volatile
    var enabled: Boolean = false

    class Result(val code: Int, val out: String, val err: String)

    /** Безопасная подстановка пути в команду оболочки. */
    fun quote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /**
     * Команда su: в глобальном пространстве монтирования (`--mount-master`, Magisk/KernelSU) —
     * иначе Android 11+ показывает в /data/data только собственную папку приложения.
     */
    @Volatile private var mountMaster = true

    private const val DEFAULT_TIMEOUT_MS = 20_000L
    private const val LIST_TIMEOUT_MS = 120_000L
    private const val READ_TIMEOUT_MS = 5L * 60L * 1000L
    private const val MAX_NAME_BYTES = 1024
    private const val MAX_HEADER_BYTES = 256
    private const val PID_MARK = "@@WEXPG@@"
    private const val S_IFMT = 0xF000
    private const val S_IFDIR = 0x4000
    private const val S_IFLNK = 0xA000

    private val LIST_HEADER = Regex("""([0-9A-Fa-f]+)\|(\d+)\|(\d+)\|(\d+)\|(\d+)\|([01])\|(\d+)""")

    private class Exec<T>(val code: Int, val value: T, val err: String)

    /** В сырой строке Kotlin `$` не экранируется, поэтому в тексте оболочки он записан как `§`. */
    private fun shell(body: String) = body.trimIndent().replace('§', '$')

    private fun suArgs(cmd: String, useMountMaster: Boolean): List<String> =
        if (useMountMaster) listOf("su", "--mount-master", "-c", cmd) else listOf("su", "-c", cmd)

    /**
     * `setsid` делает оболочку лидером группы: по отмене убивается find/stat/cat, а не только клиент su.
     * Pid группы печатается в stderr и вырезается из текста ошибки.
     */
    private fun wrapped(cmd: String): String {
        val tmpl = shell(
            """
            if command -v setsid >/dev/null 2>&1; then
              setsid sh -c @@CMD@@ &
              _wex_pid=§!
              printf '%s\n' "${PID_MARK}§_wex_pid" >&2
              wait §_wex_pid
              exit §?
            else
              printf '%s\n' "${PID_MARK}§§" >&2
              exec sh -c @@CMD@@
            fi
            """
        )
        // Подстановка после замены §, чтобы символ § в пути не превратился в $.
        return tmpl.split("@@CMD@@").joinToString(quote(cmd))
    }

    private fun mountMasterRejected(err: String): Boolean =
        err.contains("mount-master", true) || err.contains("invalid option", true) || err.contains("unknown", true)

    private fun destroy(p: Process) {
        runCatching { p.destroyForcibly() }
        runCatching { p.outputStream.close() }
        runCatching { p.inputStream.close() }
        runCatching { p.errorStream.close() }
    }

    /** Убивает группу, которую напечатал [wrapped]. Pid 0 и 1 не трогаем. */
    private fun killGroup(pid: Int, useMountMaster: Boolean) {
        if (pid <= 1) return
        runCatching {
            val killer = ProcessBuilder(suArgs("kill -9 -$pid", useMountMaster)).start()
            killer.outputStream.close()
            // Отмена может прийти с главного потока — не блокируем его ожиданием su.
            Thread {
                runCatching {
                    if (!killer.waitFor(2, TimeUnit.SECONDS)) killer.destroyForcibly()
                }
                killer.destroyForcibly()
            }.apply { isDaemon = true }.start()
        }
    }

    private fun startProcess(cmd: String, useMountMaster: Boolean, redirectOutput: ProcessBuilder.Redirect?): Process =
        try {
            ProcessBuilder(suArgs(wrapped(cmd), useMountMaster)).apply {
                if (redirectOutput != null) redirectOutput(redirectOutput)
            }.start()
        } catch (_: IOException) {
            throw IOException("Root недоступен: su не найден")
        }

    private suspend fun <T> execute(
        cmd: String,
        timeoutMs: Long,
        redirectOutput: ProcessBuilder.Redirect? = null,
        timeoutMessage: String = "Root: команда не ответила вовремя (разрешение не выдано?)",
        consume: suspend (InputStream) -> T,
    ): Exec<T> {
        val useMountMaster = mountMaster
        val first = once(cmd, timeoutMs, useMountMaster, redirectOutput, timeoutMessage, consume)
        // Один повтор без --mount-master, без вложенного вызова run().
        if (useMountMaster && first.code != 0 && mountMasterRejected(first.err)) {
            mountMaster = false
            return once(cmd, timeoutMs, false, redirectOutput, timeoutMessage, consume)
        }
        return first
    }

    private suspend fun <T> once(
        cmd: String,
        timeoutMs: Long,
        useMountMaster: Boolean,
        redirectOutput: ProcessBuilder.Redirect?,
        timeoutMessage: String,
        consume: suspend (InputStream) -> T,
    ): Exec<T> = withContext(Dispatchers.IO) {
        val p = startProcess(cmd, useMountMaster, redirectOutput)
        val group = AtomicInteger(0)
        val onCancel = coroutineContext.job.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                destroy(p)
                killGroup(group.get(), useMountMaster)
            }
        }
        try {
            // Команда stdin не читает: закрываем, чтобы su не держал ввод открытым.
            p.outputStream.close()
            supervisorScope {
                val errD = async { drainErr(p.errorStream, group) }
                val outD = async { runCatching { consume(p.inputStream) } }
                val finished = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
                ensureActive()
                if (!finished) {
                    destroy(p)
                    killGroup(group.get(), useMountMaster)
                    throw IOException(timeoutMessage)
                }
                Exec(p.exitValue(), outD.await().getOrElse { throw it }, errD.await())
            }
        } finally {
            onCancel.dispose()
            destroy(p)
        }
    }

    private fun drainErr(stream: InputStream, group: AtomicInteger): String {
        val kept = ByteArrayOutputStream()
        val line = ByteArrayOutputStream()
        fun finishLine() {
            val bytes = line.toByteArray()
            line.reset()
            val text = bytes.toString(Charsets.UTF_8)
            if (text.startsWith(PID_MARK)) {
                val n = text.removePrefix(PID_MARK).trim().toIntOrNull()
                if (n != null && n > 1 && group.get() == 0) {
                    group.set(n)
                    return
                }
            }
            if (kept.size() < 64 * 1024) {
                kept.write(bytes)
                kept.write('\n'.code)
            }
        }
        try {
            while (true) {
                val b = stream.read()
                if (b < 0) {
                    if (line.size() > 0) finishLine()
                    break
                }
                if (b == '\n'.code) finishLine()
                else if (line.size() < 64 * 1024) line.write(b)
            }
        } catch (_: IOException) {
            if (line.size() > 0) finishLine()
        }
        return kept.toString(Charsets.UTF_8).trim()
    }

    suspend fun run(cmd: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): Result {
        val r = execute(cmd, timeoutMs) { it.readBytes() }
        return Result(r.code, r.value.toString(Charsets.UTF_8), r.err)
    }

    /** Есть ли root и разрешил ли его менеджер (покажет запрос при первом вызове). */
    suspend fun check(): Boolean = runCatching { run("id -u", timeoutMs = 30_000).let { it.code == 0 && it.out.trim() == "0" } }.getOrDefault(false)

    /** Содержимое папки, недоступной приложению. */
    suspend fun list(path: String): List<FileItem> {
        val r = execute(
            listCommand(path),
            LIST_TIMEOUT_MS,
            timeoutMessage = "Root: каталог не прочитан вовремя",
        ) { input -> parseList(input, path) }
        if (r.code == 3) throw AccessDeniedException(path)
        if (r.code != 0) throw IOException("Root: ${r.err.ifBlank { "код ${r.code}" }.trim()}")
        return r.value
    }

    /**
     * Записи каталога: строка `mode|size|mtime|uid|gid|dir|len`, затем ровно len байт имени.
     * В формат `stat` NUL не положить, поэтому имя не режется по `|` и переводу строки.
     */
    fun parseList(data: ByteArray, directoryPath: String): List<FileItem> =
        parseList(ByteArrayInputStream(data), directoryPath)

    private fun parseList(input: InputStream, directoryPath: String): List<FileItem> {
        val src = input.buffered()
        val out = ArrayList<FileItem>()
        val base = directoryPath.trimEnd('/')
        while (true) {
            val header = readHeaderLine(src) ?: break
            val parsed = parseHeader(header) ?: break
            val nameBytes = readExact(src, parsed.len) ?: break
            val name = decodeName(nameBytes) ?: continue
            if (!validName(name)) continue
            val type = parsed.mode and S_IFMT
            val link = type == S_IFLNK
            out += FileItem(
                path = if (base.isEmpty()) "/$name" else "$base/$name",
                name = name,
                isDirectory = type == S_IFDIR || (link && parsed.dir),
                size = parsed.size,
                lastModified = parsed.mtime * 1000L,
                isSymlink = link,
                mode = parsed.mode,
                uid = parsed.uid,
                gid = parsed.gid,
            )
        }
        return out
    }

    private class Header(val mode: Int, val size: Long, val mtime: Long, val uid: Int, val gid: Int, val dir: Boolean, val len: Int)

    private fun parseHeader(line: ByteArray): Header? {
        val text = line.toString(Charsets.ISO_8859_1).trimEnd('\r')
        val m = LIST_HEADER.matchEntire(text) ?: return null
        val mode = m.groupValues[1].toIntOrNull(16) ?: return null
        val size = m.groupValues[2].toLongOrNull() ?: return null
        val mtime = m.groupValues[3].toLongOrNull() ?: return null
        val uid = m.groupValues[4].toIntOrNull() ?: return null
        val gid = m.groupValues[5].toIntOrNull() ?: return null
        val len = m.groupValues[7].toIntOrNull() ?: return null
        // Длина ограничена: поддельное поле не должно заставить читать гигабайты.
        if (len !in 0..MAX_NAME_BYTES) return null
        return Header(mode, size, mtime, uid, gid, m.groupValues[6] == "1", len)
    }

    private fun validName(name: String): Boolean =
        name.isNotEmpty() && name != "." && name != ".." && '/' !in name && '\u0000' !in name

    private fun decodeName(bytes: ByteArray): String? = try {
        val dec = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        dec.decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) {
        null
    }

    private fun readHeaderLine(input: InputStream): ByteArray? {
        val buf = ByteArrayOutputStream()
        while (buf.size() <= MAX_HEADER_BYTES) {
            val b = input.read()
            if (b < 0) return null
            if (b == '\n'.code) return buf.toByteArray()
            buf.write(b)
        }
        return null
    }

    private fun readExact(input: InputStream, n: Int): ByteArray? {
        if (n == 0) return ByteArray(0)
        val buf = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = input.read(buf, off, n - off)
            if (r < 0) return null
            off += r
        }
        return buf
    }

    /**
     * Один `stat` на пачку имён от `find -exec +` (не glob — он упирается в ARG_MAX).
     * Если число строк не совпало с числом имён, считаем по файлу, чтобы записи не съехали.
     */
    private fun listCommand(path: String): String {
        val inner = shell(
            """
            emit() {
              f=§1
              meta=§2
              if [ -L "§f" ]; then
                fsz=§(stat -L -c "%s" -- "§f" 2>/dev/null) || fsz=0
                mode=§{meta%%|*}
                tail=§{meta#*|}
                tail=§{tail#*|}
                meta="§mode|§fsz|§tail"
              fi
              d=0
              [ -d "§f" ] && d=1
              name=§{f##*/}
              [ -n "§name" ] || return 0
              [ "§name" != "." ] || return 0
              [ "§name" != ".." ] || return 0
              case "§name" in
                */*) return 0 ;;
              esac
              len=§(printf %s "§name" | wc -c | tr -dc 0-9)
              [ -n "§len" ] || return 0
              printf "%s|%s|%s\n" "§meta" "§d" "§len"
              printf %s "§name"
            }
            n=§#
            mf=§(mktemp 2>/dev/null) || mf=
            if [ -n "§mf" ]; then
              stat -c "%f|%s|%Y|%u|%g" -- "§@" >"§mf" 2>/dev/null
              lines=§(wc -l < "§mf" | tr -dc 0-9)
              if [ "§lines" = "§n" ]; then
                exec 3<"§mf"
                for f do
                  IFS= read -r meta <&3 || continue
                  emit "§f" "§meta"
                done
                exec 3<&-
              else
                for f do
                  meta=§(stat -c "%f|%s|%Y|%u|%g" -- "§f" 2>/dev/null) || continue
                  emit "§f" "§meta"
                done
              fi
              rm -f "§mf"
            else
              for f do
                meta=§(stat -c "%f|%s|%Y|%u|%g" -- "§f" 2>/dev/null) || continue
                emit "§f" "§meta"
              done
            fi
            """
        )
        return "cd ${quote(path)} || exit 3\nfind . -mindepth 1 -maxdepth 1 -exec sh -c ${quote(inner)} sh {} +"
    }

    /**
     * Копия закрытого файла в [dest]. Перед `cat` — `stat -L`: ссылка читается только если цель
     * обычный файл. Устройство, FIFO, сокет и каталог не открываются.
     */
    suspend fun readTo(path: String, dest: File): Unit = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()
        var ok = false
        try {
            val name = File(path).name
            val r = execute(
                readCommand(path),
                READ_TIMEOUT_MS,
                ProcessBuilder.Redirect.to(dest),
                "Root: копирование не завершилось",
            ) { ByteArray(0) }
            when (r.code) {
                0 -> ok = true
                2 -> throw IOException("Root: не удалось прочитать $name: нет такого файла")
                4 -> throw IOException("Root: $name — не обычный файл")
                else -> throw IOException("Root: не удалось прочитать $name: ${r.err.ifBlank { "код ${r.code}" }.trim()}")
            }
        } finally {
            if (!ok) dest.delete()
        }
    }

    private fun readCommand(path: String): String {
        val tmpl = shell(
            """
            if [ ! -e @@Q@@ ] && [ ! -L @@Q@@ ]; then exit 2; fi
            m=§(stat -L -c '%f' -- @@Q@@ 2>/dev/null) || exit 4
            t=§((0x§m & 0xF000))
            [ "§t" -eq §((0x8000)) ] || exit 4
            cat -- @@Q@@
            """
        )
        return tmpl.split("@@Q@@").joinToString(quote(path))
    }
}
