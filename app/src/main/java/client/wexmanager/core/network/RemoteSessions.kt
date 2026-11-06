package client.wexmanager.core.network

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Пул открытых соединений по id подключения.
 * Экраны берут ссылку ([acquire]/[release]); без ссылок соединение закрывается сразу,
 * при простое дольше 5 минут — тоже (и прозрачно переоткрывается при следующем обращении).
 * Операции на одном соединении выполняются по очереди (FTP/SFTP-канал не многопоточны).
 */
object RemoteSessions {
    private const val IDLE_TIMEOUT_MS = 5 * 60_000L
    private const val REAPER_PERIOD_MS = 30_000L

    /** Простой, после которого соединение проверяется перед неидемпотентной операцией. */
    private const val PROBE_IDLE_MS = 15_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private class Slot(val id: String) {
        val lock = Mutex()

        @Volatile
        var fs: RemoteFileSystem? = null

        @Volatile
        var lastUsed: Long = System.currentTimeMillis()

        /** Параметры, с которыми открыто [fs]: изменились — переподключаемся. */
        var openedWith: RemoteConnection? = null

        /** Под synchronized(slots). */
        var refs = 0
        var closeJob: Job? = null
    }

    private val slots = HashMap<String, Slot>()
    private var reaper: Job? = null

    private fun slot(id: String): Slot = synchronized(slots) { slots.getOrPut(id) { Slot(id) } }

    fun acquire(id: String) {
        synchronized(slots) {
            val s = slots.getOrPut(id) { Slot(id) }
            s.refs++
            s.closeJob?.cancel()
            s.closeJob = null
            ensureReaper()
        }
    }

    fun release(id: String) {
        synchronized(slots) {
            val s = slots[id] ?: return
            s.refs = (s.refs - 1).coerceAtLeast(0)
            if (s.refs == 0) {
                s.closeJob?.cancel()
                s.closeJob = scope.launch { closeSlot(s) }
            }
        }
    }

    /** Закрыть соединение (например, после изменения параметров подключения). */
    fun invalidate(id: String) {
        val s = synchronized(slots) { slots[id] } ?: return
        scope.launch { closeSlot(s) }
    }

    fun closeAll() {
        val all = synchronized(slots) { slots.values.toList() }
        all.forEach { s -> scope.launch { closeSlot(s) } }
    }

    private suspend fun closeSlot(s: Slot) {
        s.lock.withLock {
            val fs = s.fs ?: return@withLock
            s.fs = null
            withContext(NonCancellable) { fs.closeQuietly() }
        }
    }

    /**
     * Выполнить [block] на общем соединении подключения. Если переиспользованное соединение
     * оказалось оборванным — один раз переподключается и повторяет операцию.
     * [retryOnLost] = false — для неидемпотентных операций (удаление, переименование, создание,
     * загрузка): часть могла выполниться до обрыва, повтор опасен.
     */
    suspend fun <T> use(
        context: Context,
        connection: RemoteConnection,
        retryOnLost: Boolean = true,
        block: suspend (RemoteFileSystem) -> T,
    ): T {
        val s = slot(connection.id)
        return s.lock.withLock {
            var retried = false
            while (true) {
                val existing = s.fs
                val reused = existing != null && existing.isAlive && s.openedWith == connection
                val idleMs = System.currentTimeMillis() - s.lastUsed
                val fs = if (reused) existing else {
                    if (existing != null) {
                        s.fs = null
                        withContext(Dispatchers.IO + NonCancellable) { existing.closeQuietly() }
                    }
                    RemoteFs.open(context, connection).also {
                        s.fs = it
                        s.openedWith = connection
                    }
                }
                // Повторить неидемпотентную операцию нельзя — давно простаивавшее соединение
                // проверяем заранее безопасным запросом и при обрыве открываем новое.
                if (reused && !retryOnLost && idleMs > PROBE_IDLE_MS && !probe(fs)) {
                    s.fs = null
                    withContext(Dispatchers.IO + NonCancellable) { fs.closeQuietly() }
                    continue
                }
                s.lastUsed = System.currentTimeMillis()
                try {
                    val r = block(fs)
                    s.lastUsed = System.currentTimeMillis()
                    return@withLock r
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    s.lastUsed = System.currentTimeMillis()
                    val dead = e.isConnectionLost() || !fs.isAlive
                    if (dead) {
                        s.fs = null
                        withContext(Dispatchers.IO + NonCancellable) { fs.closeQuietly() }
                    }
                    if (dead && reused && retryOnLost && !retried) {
                        retried = true
                        continue
                    }
                    throw e
                }
            }
            @Suppress("UNREACHABLE_CODE")
            throw IllegalStateException()
        }
    }

    /** false — соединение оборвано. */
    private suspend fun probe(fs: RemoteFileSystem): Boolean = try {
        fs.stat(fs.home)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        !e.isConnectionLost() && fs.isAlive
    }

    /** Отдельное соединение для передач (не блокирует просмотр). Закрывает вызывающий. */
    suspend fun openDedicated(context: Context, connection: RemoteConnection): RemoteFileSystem =
        RemoteFs.open(context, connection)

    private fun ensureReaper() {
        if (reaper?.isActive == true) return
        reaper = scope.launch {
            while (true) {
                delay(REAPER_PERIOD_MS)
                val now = System.currentTimeMillis()
                val all = synchronized(slots) { slots.values.toList() }
                for (s in all) {
                    if (s.fs != null && now - s.lastUsed > IDLE_TIMEOUT_MS && s.lock.tryLock()) {
                        try {
                            val fs = s.fs
                            s.fs = null
                            fs?.closeQuietly()
                        } finally {
                            s.lock.unlock()
                        }
                    }
                }
                val idle = synchronized(slots) {
                    val done = slots.values.all { it.refs == 0 && it.fs == null }
                    if (done) reaper = null
                    done
                }
                if (idle) break
            }
        }
    }
}
