package client.wexmanager.feature.terminal

import android.app.Application
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException

internal const val TERMINAL_MIN_FONT_SP = 10
internal const val TERMINAL_MAX_FONT_SP = 22

@Immutable
data class TerminalState(
    val cwd: String = "/",
    val alive: Boolean = false,
    val exited: Boolean = false,
    val error: String? = null,
    val cols: Int = 0,
    val rows: Int = 0,
    val fontSp: Int = 13,
    val keepScreenOn: Boolean = false,
    val scrollback: List<TermLine> = emptyList(),
    val screen: List<TermLine> = emptyList(),
    val revision: Long = 0,
)

/**
 * Интерактивная оболочка на PTY. Байт в мастер уходит как есть, cwd читается из /proc/pid/cwd.
 * Запись и старт стоят в одной очереди, чтобы нажатия не обогнали fork.
 */
class TerminalViewModel(app: Application, startDir: String) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(TerminalState(cwd = startDir.ifEmpty { "/" }))
    val state: StateFlow<TerminalState> = _state.asStateFlow()

    private val control = Dispatchers.IO.limitedParallelism(1)
    private val screenLock = Any()
    private val life = Any()
    private val screen = VtScreen()
    private val early = ArrayList<ByteArray>()

    @Volatile private var pty: Pty? = null
    @Volatile private var generation = 0
    @Volatile private var cleared = false
    private var booted = false
    private var phase = Phase.STARTING

    init {
        val failed = Pty.loadError
        if (failed != null) {
            phase = Phase.FAILED
            _state.update {
                it.copy(error = if (failed.isBlank()) "Не удалось загрузить терминал" else "Не удалось загрузить терминал: $failed")
            }
        }
    }

    fun resize(cols: Int, rows: Int) {
        if (cols < 2 || rows < 2) return
        viewModelScope.launch(control) {
            if (phase == Phase.FAILED && Pty.loadError != null) return@launch
            val action = synchronized(screenLock) {
                val changed = screen.resize(cols, rows)
                val first = !booted && phase != Phase.FAILED
                if (first) booted = true
                if (first || changed) publishLocked()
                if (first) 1 else if (changed) 2 else 0
            }
            when (action) {
                1 -> startPty(screen.cols, screen.rows)
                2 -> {
                    val session = pty
                    if (session != null && !session.destroyed) {
                        try {
                            session.setSize(screen.rows, screen.cols)
                        } catch (_: IOException) {
                        }
                    }
                }
            }
        }
    }

    fun write(data: ByteArray) {
        if (data.isEmpty()) return
        val copy = data.copyOf()
        viewModelScope.launch(control) {
            val session = pty
            if (session == null || phase != Phase.RUNNING || session.destroyed) {
                if (phase == Phase.STARTING) early.add(copy)
                return@launch
            }
            writePty(session.fileDescriptor, copy)
        }
    }

    fun type(text: String, ctrl: Boolean) {
        if (text.isEmpty()) return
        if (ctrl && text.length == 1) {
            ctrlByte(text[0])?.let {
                write(byteArrayOf(it.toByte()))
                return
            }
        }
        val normalized = text.replace('\n', '\r')
        if (normalized.length > 1 && screen.bracketedPaste) {
            write("\u001b[200~".encodeToByteArray())
            write(normalized.encodeToByteArray())
            write("\u001b[201~".encodeToByteArray())
        } else {
            write(normalized.encodeToByteArray())
        }
    }

    fun enter() = write(byteArrayOf(13))

    fun tab() = write(byteArrayOf(9))

    fun esc() = write(byteArrayOf(0x1B))

    fun backspace() = write(byteArrayOf(0x7F))

    fun ctrlC() = write(byteArrayOf(3))

    fun arrow(letter: Char) {
        val sequence = if (screen.applicationCursor) "\u001bO$letter" else "\u001b[$letter"
        write(sequence.encodeToByteArray())
    }

    fun home() {
        write((if (screen.applicationCursor) "\u001bOH" else "\u001b[H").encodeToByteArray())
    }

    fun end() {
        write((if (screen.applicationCursor) "\u001bOF" else "\u001b[F").encodeToByteArray())
    }

    fun clear() {
        viewModelScope.launch(control) {
            synchronized(screenLock) {
                screen.clearDisplay()
                publishLocked()
            }
        }
    }

    fun restart() {
        viewModelScope.launch(control) {
            if (Pty.loadError != null) return@launch
            stopSession()
            synchronized(screenLock) {
                screen.archiveAndReset()
                publishLocked()
            }
            if (screen.cols >= 2 && screen.rows >= 2) startPty(screen.cols, screen.rows)
        }
    }

    fun setFontSp(size: Int) {
        val next = size.coerceIn(TERMINAL_MIN_FONT_SP, TERMINAL_MAX_FONT_SP)
        _state.update { if (it.fontSp == next) it else it.copy(fontSp = next) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        _state.update { if (it.keepScreenOn == enabled) it else it.copy(keepScreenOn = enabled) }
    }

    fun textForCopy(): String = synchronized(screenLock) { screen.copyText() }

    private fun startPty(cols: Int, rows: Int) {
        val dir = File(_state.value.cwd).let { if (it.isDirectory) it.absolutePath else "/" }
        val home = getApplication<Application>().filesDir.absolutePath
        val session = try {
            Pty.open(dir, home, rows, cols)
        } catch (error: IOException) {
            failStart(error.message)
            return
        } catch (error: ErrnoException) {
            failStart(error.message)
            return
        }
        if (!install(session)) {
            session.destroy()
            return
        }
        try {
            val flags = Os.fcntlInt(session.fileDescriptor, OsConstants.F_GETFL, 0)
            Os.fcntlInt(session.fileDescriptor, OsConstants.F_SETFL, flags or OsConstants.O_NONBLOCK)
        } catch (_: ErrnoException) {
        }
        phase = Phase.RUNNING
        val cwd = readCwd(session.pid) ?: dir
        _state.update { it.copy(alive = true, exited = false, error = null, cwd = cwd) }
        val pending = early.toList()
        early.clear()
        for (chunk in pending) writePty(session.fileDescriptor, chunk)
        val gen = generation
        viewModelScope.launch(Dispatchers.IO) { readLoop(session, gen) }
    }

    private fun failStart(message: String?) {
        phase = Phase.FAILED
        early.clear()
        val text = message?.takeIf { it.isNotBlank() } ?: "неизвестная ошибка"
        _state.update { it.copy(alive = false, exited = false, error = "Не удалось запустить оболочку: $text") }
    }

    private fun install(session: Pty): Boolean = synchronized(life) {
        if (cleared) return false
        pty = session
        true
    }

    private fun stopSession() {
        generation++
        phase = Phase.STARTING
        early.clear()
        val session = synchronized(life) {
            val current = pty
            pty = null
            current
        }
        session?.destroy()
        _state.update { it.copy(alive = false, exited = false) }
    }

    private suspend fun readLoop(session: Pty, gen: Int) {
        val buf = ByteArray(8192)
        val pollFd = StructPollfd()
        pollFd.fd = session.fileDescriptor
        pollFd.events = OsConstants.POLLIN.toShort()
        val polls = arrayOf(pollFd)
        var ticks = 0
        var eof = false
        while (currentCoroutineContext().isActive && gen == generation && !session.destroyed) {
            pollFd.revents = 0
            val ready = try {
                Os.poll(polls, 400)
            } catch (error: ErrnoException) {
                if (error.errno == OsConstants.EINTR) continue
                eof = true
                break
            }
            if (ready > 0) {
                while (true) {
                    val n = try {
                        Os.read(session.fileDescriptor, buf, 0, buf.size)
                    } catch (error: ErrnoException) {
                        if (error.errno == OsConstants.EINTR) continue
                        if (error.errno == OsConstants.EAGAIN) break
                        eof = true
                        break
                    } catch (error: InterruptedIOException) {
                        if (error.bytesTransferred > 0) deliver(session, gen, buf, error.bytesTransferred)
                        continue
                    }
                    if (n <= 0) {
                        eof = true
                        break
                    }
                    deliver(session, gen, buf, n)
                }
            }
            val events = pollFd.revents.toInt() and 0xFFFF
            if (events and (OsConstants.POLLHUP or OsConstants.POLLERR or OsConstants.POLLNVAL) != 0) eof = true
            ticks++
            if (ticks % 2 == 0 && gen == generation) publishCwd(session.pid)
            if (eof) break
        }
        if (gen == generation && !cleared) {
            viewModelScope.launch(control) {
                if (gen != generation || phase != Phase.RUNNING) return@launch
                phase = Phase.EXITED
                early.clear()
                synchronized(life) {
                    if (pty === session) pty = null
                }
                session.destroy()
                _state.update { it.copy(alive = false, exited = true) }
            }
        }
    }

    private fun deliver(session: Pty, gen: Int, buf: ByteArray, n: Int) {
        val replies = synchronized(screenLock) {
            if (gen != generation || session.destroyed) return@synchronized emptyList()
            screen.feed(buf, n)
            val pending = screen.takeReplies()
            publishLocked()
            pending
        }
        if (session.destroyed) return
        for (reply in replies) writePty(session.fileDescriptor, reply)
    }

    private fun publishLocked() {
        val snap = screen.snapshot()
        _state.update {
            it.copy(
                cols = snap.cols,
                rows = snap.rows,
                scrollback = snap.scrollback,
                screen = snap.screen,
                revision = it.revision + 1,
            )
        }
    }

    private fun publishCwd(pid: Int) {
        val cwd = readCwd(pid) ?: return
        _state.update { if (it.cwd == cwd) it else it.copy(cwd = cwd) }
    }

    private fun readCwd(pid: Int): String? = try {
        Os.readlink("/proc/$pid/cwd")
    } catch (_: ErrnoException) {
        null
    }

    override fun onCleared() {
        val session = synchronized(life) {
            cleared = true
            val current = pty
            pty = null
            current
        }
        session?.destroy()
    }

    private enum class Phase { STARTING, RUNNING, EXITED, FAILED }

    private companion object {
        fun ctrlByte(ch: Char): Int? = when (ch) {
            in 'a'..'z' -> ch - 'a' + 1
            in 'A'..'Z' -> ch - 'A' + 1
            ' ' -> 0
            else -> null
        }
    }
}
