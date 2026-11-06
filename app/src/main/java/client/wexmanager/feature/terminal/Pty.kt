package client.wexmanager.feature.terminal

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import java.io.FileDescriptor
import java.io.IOException

/**
 * Мастер PTY и pid оболочки. Библиотека wexpty: posix_openpt, fork, exec /system/bin/sh.
 * Если .so не загрузилась, [loadError] не пустой — запасной оболочки нет.
 */
internal class Pty private constructor(
    val pid: Int,
    private val pfd: ParcelFileDescriptor,
) {
    @Volatile
    var destroyed: Boolean = false
        private set

    val fileDescriptor: FileDescriptor
        get() = pfd.fileDescriptor

    fun setSize(rows: Int, cols: Int) {
        if (destroyed) return
        try {
            nativeSetSize(pfd.fd, rows, cols)
        } catch (_: Exception) {
        }
    }

    fun destroy() {
        if (destroyed) return
        synchronized(this) {
            if (destroyed) return
            val fd = try {
                pfd.fd
            } catch (_: Throwable) {
                -1
            }
            destroyed = true
            try {
                nativeKillGroup(pid, fd)
            } catch (_: Throwable) {
            }
            try {
                pfd.close()
            } catch (_: IOException) {
            }
        }
    }

    companion object {
        val loadError: String? = try {
            System.loadLibrary("wexpty")
            null
        } catch (error: Throwable) {
            error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
        }

        fun open(cwd: String, home: String, rows: Int, cols: Int): Pty {
            val pair = nativeOpen(cwd, home, rows, cols)
            if (pair.size < 2) throw IOException("pty")
            val raw = pair[0]
            val pid = pair[1]
            try {
                return Pty(pid, ParcelFileDescriptor.adoptFd(raw))
            } catch (error: RuntimeException) {
                runCatching { nativeKillGroup(pid, raw) }
                nativeClose(raw)
                throw error
            }
        }

        @JvmStatic
        private external fun nativeOpen(cwd: String, home: String, rows: Int, cols: Int): IntArray

        @JvmStatic
        private external fun nativeSetSize(fd: Int, rows: Int, cols: Int)

        @JvmStatic
        private external fun nativeKillGroup(pid: Int, fd: Int)

        @JvmStatic
        private external fun nativeClose(fd: Int)
    }
}

private val ptyWriteLock = Any()

internal fun writePty(fd: FileDescriptor, data: ByteArray) {
    synchronized(ptyWriteLock) {
        writePtyLocked(fd, data)
    }
}

private fun writePtyLocked(fd: FileDescriptor, data: ByteArray) {
    var offset = 0
    var spins = 0
    while (offset < data.size && spins < 80) {
        val wrote = try {
            Os.write(fd, data, offset, data.size - offset)
        } catch (error: ErrnoException) {
            if (error.errno == OsConstants.EINTR) continue
            if (error.errno == OsConstants.EAGAIN) {
                spins++
                val poll = StructPollfd()
                poll.fd = fd
                poll.events = OsConstants.POLLOUT.toShort()
                try {
                    Os.poll(arrayOf(poll), 40)
                } catch (_: ErrnoException) {
                }
                continue
            }
            return
        }
        if (wrote <= 0) return
        offset += wrote
        spins = 0
    }
}
