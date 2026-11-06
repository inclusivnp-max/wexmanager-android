package client.wexmanager.feature.analyzer

import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import client.wexmanager.core.fs.FileKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.PriorityQueue

/** Категории для разбивки занятого места. [sample] — вид файла, по которому берётся цвет иконки. */
enum class Category(val label: String, val icon: ImageVector, val sample: FileKind) {
    Images("Изображения", Icons.Rounded.Image, FileKind.Image),
    Video("Видео", Icons.Rounded.Movie, FileKind.Video),
    Audio("Аудио", Icons.Rounded.MusicNote, FileKind.Audio),
    Documents("Документы", Icons.Rounded.Description, FileKind.Document),
    Archives("Архивы", Icons.Rounded.FolderZip, FileKind.Archive),
    Apk("APK", Icons.Rounded.Android, FileKind.Apk),
    Other("Прочее", Icons.Rounded.Category, FileKind.Unknown);

    companion object {
        fun of(kind: FileKind): Category = when (kind) {
            FileKind.Image, FileKind.Vector -> Images
            FileKind.Video -> Video
            FileKind.Audio -> Audio
            FileKind.Pdf, FileKind.Document, FileKind.Spreadsheet, FileKind.Presentation, FileKind.Ebook, FileKind.Text -> Documents
            FileKind.Archive -> Archives
            FileKind.Apk -> Apk
            else -> Other
        }

        fun ofName(name: String): Category = of(FileKind.of(name, false))
    }
}

// ------------------------------------------------------------------ Дерево

/**
 * Неизменяемый узел дерева. Полный путь не хранится (экономия памяти на сотнях тысяч файлов) —
 * он восстанавливается при навигации от корня.
 */
@Immutable
sealed class Node {
    abstract val name: String
    abstract val size: Long
    abstract val lastModified: Long
    abstract val isDir: Boolean
}

@Immutable
class FileNode(
    override val name: String,
    override val size: Long,
    override val lastModified: Long,
) : Node() {
    override val isDir: Boolean get() = false
}

@Immutable
class DirNode(
    override val name: String,
    override val size: Long,
    override val lastModified: Long,
    /** Отсортированы по размеру (по убыванию). */
    val children: List<Node>,
    /** Файлов во всём поддереве. */
    val fileCount: Int,
    /** Папок во всём поддереве (без самой папки). */
    val dirCount: Int,
    /** Байты по [Category.ordinal] во всём поддереве. */
    val categories: LongArray,
    /** Папку не удалось прочитать. */
    val unreadable: Boolean = false,
) : Node() {
    override val isDir: Boolean get() = true

    fun child(name: String): Node? = children.firstOrNull { it.name == name }
}

@Immutable
data class FileEntry(val path: String, val size: Long, val lastModified: Long) {
    val name: String get() = path.substringAfterLast('/')
}

@Immutable
data class ScanProgress(val files: Int = 0, val dirs: Int = 0, val bytes: Long = 0, val current: String = "")

class ScanResult(
    val root: DirNode,
    val largest: List<FileEntry>,
    val emptyDirs: List<String>,
    val unreadable: Int,
    val durationMs: Long,
)

private fun Node.categoryBytes(): LongArray = when (this) {
    is DirNode -> categories
    is FileNode -> LongArray(Category.entries.size).also { it[Category.ofName(name).ordinal] = size }
}

private fun Node.files(): Int = if (this is DirNode) fileCount else 1
private fun Node.dirs(): Int = if (this is DirNode) dirCount + 1 else 0

/**
 * Удаляет узел по относительному пути [names] (сегменты от этого узла) и пересчитывает
 * размеры всех предков. Возвращает новое дерево или `null`, если узел не найден.
 */
fun DirNode.without(names: List<String>): DirNode? {
    if (names.isEmpty()) return null
    val idx = children.indexOfFirst { it.name == names[0] }
    if (idx < 0) return null
    val old = children[idx]
    val replacement: Node? = if (names.size == 1) {
        null
    } else {
        if (old !is DirNode) return null
        old.without(names.subList(1, names.size)) ?: return null
    }
    val newChildren = ArrayList<Node>(children.size)
    children.forEachIndexed { i, n -> if (i != idx) newChildren += n }
    if (replacement != null) {
        newChildren += replacement
        newChildren.sortByDescending { it.size }
    }
    val oldCats = old.categoryBytes()
    val newCats = replacement?.categoryBytes()
    val cats = LongArray(categories.size) { i -> categories[i] - oldCats[i] + (newCats?.get(i) ?: 0L) }
    return DirNode(
        name = name,
        size = size - old.size + (replacement?.size ?: 0L),
        lastModified = lastModified,
        children = newChildren,
        fileCount = fileCount - old.files() + (replacement?.files() ?: 0),
        dirCount = dirCount - old.dirs() + (replacement?.dirs() ?: 0),
        categories = cats,
        unreadable = unreadable,
    )
}

// ------------------------------------------------------------------ Сканер

object DiskScanner {
    private const val TOP_FILES = 100
    private const val MAX_EMPTY = 2000
    private const val PROGRESS_INTERVAL_MS = 90L
    private val systemDirs = setOf("/proc", "/sys", "/dev", "/acct", "/d", "/config")

    private class Pending(val path: String, val name: String, val mtime: Long)

    private class Frame(val path: String, val name: String, val mtime: Long) {
        val pending = ArrayDeque<Pending>()
        val children = ArrayList<Node>()
        var size = 0L
        var files = 0
        var dirs = 0
        val cats = LongArray(Category.entries.size)
        var unreadable = false
    }

    /**
     * Обходит [rootPath] без следования симлинкам (они считаются файлами собственного размера).
     * Обход итеративный — стек кадров, поэтому глубина вложенности не ограничена стеком потока.
     */
    suspend fun scan(rootPath: String, onProgress: (ScanProgress) -> Unit): ScanResult = withContext(Dispatchers.IO) {
        val started = SystemClock.elapsedRealtime()
        val top = PriorityQueue<FileEntry>(TOP_FILES + 1, compareBy { it.size })
        val empty = ArrayList<String>()
        var files = 0
        var dirs = 0
        var bytes = 0L
        var unreadable = 0
        var tick = 0
        var lastPublish = 0L

        fun publish(current: String, force: Boolean = false) {
            val now = SystemClock.uptimeMillis()
            if (force || now - lastPublish >= PROGRESS_INTERVAL_MS) {
                lastPublish = now
                onProgress(ScanProgress(files, dirs, bytes, current))
            }
        }

        fun open(path: String, name: String, mtime: Long): Frame {
            val frame = Frame(path, name, mtime)
            val names = File(path).list()
            if (names == null) {
                frame.unreadable = true
                unreadable++
                return frame
            }
            if (names.isEmpty() && empty.size < MAX_EMPTY) empty += path
            for (n in names) {
                if (++tick and 255 == 0) {
                    ensureActive()
                    publish(path)
                }
                val child = if (path == "/") "/$n" else "$path/$n"
                val st = try {
                    Os.lstat(child)
                } catch (_: Exception) {
                    continue
                }
                val childMtime = st.st_mtime * 1000L
                if (OsConstants.S_ISDIR(st.st_mode)) {
                    if (n != ".WexTrash" && child !in systemDirs) frame.pending.addLast(Pending(child, n, childMtime))
                } else {
                    val size = st.st_size.coerceAtLeast(0L)
                    frame.children += FileNode(n, size, childMtime)
                    frame.size += size
                    frame.files++
                    frame.cats[Category.ofName(n).ordinal] += size
                    files++
                    bytes += size
                    if (top.size < TOP_FILES) {
                        top += FileEntry(child, size, childMtime)
                    } else if (top.peek()!!.size < size) {
                        top.poll()
                        top += FileEntry(child, size, childMtime)
                    }
                }
            }
            return frame
        }

        val rootFile = File(rootPath)
        val stack = ArrayDeque<Frame>()
        stack.addLast(open(rootPath, rootFile.name.ifEmpty { rootPath }, rootFile.lastModified()))
        var result: DirNode? = null
        while (stack.isNotEmpty()) {
            ensureActive()
            val frame = stack.last()
            val next = frame.pending.removeFirstOrNull()
            if (next != null) {
                dirs++
                stack.addLast(open(next.path, next.name, next.mtime))
                continue
            }
            stack.removeLast()
            frame.children.sortByDescending { it.size }
            frame.children.trimToSize()
            val node = DirNode(
                name = frame.name,
                size = frame.size,
                lastModified = frame.mtime,
                children = frame.children,
                fileCount = frame.files,
                dirCount = frame.dirs,
                categories = frame.cats,
                unreadable = frame.unreadable,
            )
            val parent = stack.lastOrNull()
            if (parent == null) {
                result = node
            } else {
                parent.children += node
                parent.size += node.size
                parent.files += node.fileCount
                parent.dirs += node.dirCount + 1
                for (i in parent.cats.indices) parent.cats[i] += node.categories[i]
            }
        }
        publish(rootPath, force = true)
        ScanResult(
            root = result!!,
            largest = top.sortedByDescending { it.size },
            emptyDirs = empty,
            unreadable = unreadable,
            durationMs = SystemClock.elapsedRealtime() - started,
        )
    }
}
