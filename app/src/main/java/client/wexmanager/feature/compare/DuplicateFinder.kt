package client.wexmanager.feature.compare

import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import androidx.compose.runtime.Immutable
import client.wexmanager.core.fs.NaturalOrder
import client.wexmanager.core.ops.TrashManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

enum class DupPhase(val title: String) {
    Scan("Сканирование"),
    Compare("Сравнение"),
    Verify("Проверка"),
}

@Immutable
data class DupProgress(
    val phase: DupPhase = DupPhase.Scan,
    val files: Int = 0,
    val bytes: Long = 0,
    val done: Long = 0,
    val total: Long = 0,
    val current: String = "",
) {
    /** `null` — прогресс неопределённый (фаза обхода). */
    val fraction: Float? get() = if (phase == DupPhase.Scan || total <= 0) null else (done.toDouble() / total).toFloat().coerceIn(0f, 1f)
}

@Immutable
data class DupFile(val path: String, val size: Long, val lastModified: Long) {
    val name: String get() = path.substringAfterLast('/')
    val parent: String get() = path.substringBeforeLast('/', "/").ifEmpty { "/" }
}

@Immutable
data class DupGroup(val key: String, val size: Long, val files: List<DupFile>) {
    /** Сколько освободится, если оставить одну копию. */
    val wasted: Long get() = size * (files.size - 1).coerceAtLeast(0)
}

class DupResult(
    val groups: List<DupGroup>,
    val scannedFiles: Int,
    val scannedBytes: Long,
    val unreadable: Int,
    val durationMs: Long,
)

/**
 * Поиск дубликатов в три фазы: группировка по размеру → хеш первых 64 КБ →
 * полный SHA-1 только для оставшихся кандидатов (крупнее 64 КБ).
 */
object DuplicateFinder {
    private const val HEAD = 64 * 1024
    private const val WORKERS = 3
    private const val PROGRESS_MS = 100L
    private val systemDirs = setOf("/proc", "/sys", "/dev", "/acct", "/d", "/config")

    private class Entry(val path: String, val mtime: Long)

    suspend fun find(root: String, minSize: Long, onProgress: (DupProgress) -> Unit): DupResult = withContext(Dispatchers.IO) {
        val started = SystemClock.elapsedRealtime()
        val min = minSize.coerceAtLeast(1L)

        // ---------------- Фаза 1: обход и группировка по размеру.
        val bySize = HashMap<Long, ArrayList<Entry>>()
        val hardLinks = HashSet<Pair<Long, Long>>()
        var files = 0
        var bytes = 0L
        var unreadable = 0
        var tick = 0
        var last = 0L
        val stack = ArrayDeque<String>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            ensureActive()
            val dir = stack.removeLast()
            val names = File(dir).list()
            if (names == null) {
                // Сюда же попадают Android/data и Android/obb без доступа.
                unreadable++
                continue
            }
            for (n in names) {
                if (++tick and 255 == 0) {
                    ensureActive()
                    val now = SystemClock.uptimeMillis()
                    if (now - last >= PROGRESS_MS) {
                        last = now
                        onProgress(DupProgress(DupPhase.Scan, files, bytes, current = dir))
                    }
                }
                val child = childPath(dir, n)
                val st = try {
                    Os.lstat(child)
                } catch (_: Exception) {
                    continue
                }
                when {
                    OsConstants.S_ISDIR(st.st_mode) -> {
                        if (n != TrashManager.TRASH_DIR && child !in systemDirs) stack.addLast(child)
                    }
                    // Симлинки, сокеты и прочие специальные файлы не рассматриваем.
                    OsConstants.S_ISREG(st.st_mode) -> {
                        files++
                        bytes += st.st_size
                        if (st.st_size < min) continue
                        // Жёсткие ссылки на один и тот же inode — не дубликаты (место не освободится).
                        if (st.st_nlink > 1 && !hardLinks.add(st.st_dev to st.st_ino)) continue
                        bySize.getOrPut(st.st_size) { ArrayList(2) } += Entry(child, st.st_mtime * 1000L)
                    }
                }
            }
        }
        onProgress(DupProgress(DupPhase.Scan, files, bytes, current = root))

        // ---------------- Фаза 2: хеш первых 64 КБ.
        val candidates = ArrayList<Pair<Long, Entry>>()
        for ((size, list) in bySize) if (list.size >= 2) for (e in list) candidates += size to e
        bySize.clear()
        val headHashes = hashAll(
            candidates,
            phase = DupPhase.Compare,
            files = files,
            bytes = bytes,
            total = candidates.size.toLong(),
            onProgress = onProgress,
            countBytes = false,
        ) { (size, e), md, buf, job, _ -> hashRange(e.path, 0L, minOf(size, HEAD.toLong()), md, buf, job, null) }

        val byHead = HashMap<String, ArrayList<Pair<Long, Entry>>>()
        candidates.forEachIndexed { i, c ->
            val h = headHashes[i] ?: return@forEachIndexed
            byHead.getOrPut("${c.first}:$h") { ArrayList(2) } += c
        }

        val result = ArrayList<DupGroup>()
        val needFull = ArrayList<Pair<Long, Entry>>()
        val needFullKey = ArrayList<String>()
        for ((key, list) in byHead) {
            if (list.size < 2) continue
            val size = list[0].first
            if (size <= HEAD) {
                // Файл целиком уместился в первые 64 КБ — хеш уже полный.
                result += group(key, size, list.map { it.second })
            } else {
                for (c in list) {
                    needFull += c
                    needFullKey += key
                }
            }
        }
        byHead.clear()

        // ---------------- Фаза 3: полный SHA-1 (хвост после первых 64 КБ, голова уже совпала).
        if (needFull.isNotEmpty()) {
            val totalBytes = needFull.sumOf { it.first - HEAD }
            val tails = hashAll(
                needFull,
                phase = DupPhase.Verify,
                files = files,
                bytes = bytes,
                total = totalBytes,
                onProgress = onProgress,
                countBytes = true,
            ) { (size, e), md, buf, job, counter -> hashRange(e.path, HEAD.toLong(), size, md, buf, job, counter) }
            val byFull = HashMap<String, ArrayList<Pair<Long, Entry>>>()
            needFull.forEachIndexed { i, c ->
                val h = tails[i] ?: return@forEachIndexed
                byFull.getOrPut(needFullKey[i] + ":" + h) { ArrayList(2) } += c
            }
            for ((key, list) in byFull) if (list.size >= 2) result += group(key, list[0].first, list.map { it.second })
        }

        result.sortWith(compareByDescending<DupGroup> { it.wasted }.thenBy { it.files.first().path })
        DupResult(result, files, bytes, unreadable, SystemClock.elapsedRealtime() - started)
    }

    private fun group(key: String, size: Long, entries: List<Entry>): DupGroup =
        DupGroup(
            key = key,
            size = size,
            files = entries
                .map { DupFile(it.path, size, it.mtime) }
                .sortedWith { a, b -> NaturalOrder.compare(a.path, b.path) },
        )

    /**
     * Хеширует элементы несколькими потоками; `null` — файл прочитать не удалось.
     * Прогресс публикуется отдельной корутиной по таймеру (счётчики атомарные).
     */
    private suspend fun hashAll(
        items: List<Pair<Long, Entry>>,
        phase: DupPhase,
        files: Int,
        bytes: Long,
        total: Long,
        onProgress: (DupProgress) -> Unit,
        countBytes: Boolean,
        block: (Pair<Long, Entry>, MessageDigest, ByteArray, Job, AtomicLong) -> String,
    ): Array<String?> = coroutineScope {
        val out = arrayOfNulls<String>(items.size)
        val next = AtomicInteger(0)
        val done = AtomicLong(0)
        val currentName = AtomicReference("")
        onProgress(DupProgress(phase, files, bytes, 0, total))
        val reporter = launch {
            while (isActive) {
                delay(PROGRESS_MS)
                onProgress(DupProgress(phase, files, bytes, done.get(), total, currentName.get()))
            }
        }
        val workers = List(WORKERS) {
            launch(Dispatchers.IO) {
                val job = currentCoroutineContext()[Job]!!
                val md = MessageDigest.getInstance("SHA-1")
                val buf = ByteArray(HEAD)
                while (true) {
                    job.ensureActive()
                    val i = next.getAndIncrement()
                    if (i >= items.size) break
                    val item = items[i]
                    currentName.set(item.second.path)
                    md.reset()
                    out[i] = try {
                        block(item, md, buf, job, done)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                    if (!countBytes) done.incrementAndGet()
                }
            }
        }
        workers.forEach { it.join() }
        reporter.cancel()
        onProgress(DupProgress(phase, files, bytes, total, total))
        out
    }

    /** SHA-1 диапазона [from, to) файла. [counter] — счётчик прочитанных байт для прогресса. */
    private fun hashRange(path: String, from: Long, to: Long, md: MessageDigest, buf: ByteArray, job: Job, counter: AtomicLong?): String {
        FileInputStream(path).use { input ->
            val ch = input.channel
            if (from > 0) ch.position(from)
            var remaining = to - from
            var chunks = 0
            while (remaining > 0) {
                val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                if (n < 0) break
                md.update(buf, 0, n)
                remaining -= n
                counter?.addAndGet(n.toLong())
                if (++chunks and 15 == 0) job.ensureActive()
            }
            // Файл укоротился во время чтения — считаем его уникальным.
            if (remaining > 0) throw java.io.EOFException()
        }
        val digest = md.digest()
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v shr 4]).append(HEX[v and 0xF])
        }
        return sb.toString()
    }

    private const val HEX = "0123456789abcdef"
}
