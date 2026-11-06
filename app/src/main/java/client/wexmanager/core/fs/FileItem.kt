package client.wexmanager.core.fs

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Элемент списка файлов. Для элементов внутри архива [archivePath] указывает на сам архив,
 * а [path] — путь внутри него (без ведущего «/»).
 */
@Immutable
data class FileItem(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val isHidden: Boolean = name.startsWith('.'),
    val isSymlink: Boolean = false,
    val mode: Int = -1,
    val childCount: Int = -1,
    val linkTarget: String? = null,
    val archivePath: String? = null,
    val uid: Int = -1,
    val gid: Int = -1,
) {
    val extension: String = if (isDirectory) "" else name.substringAfterLast('.', "").lowercase()
    val kind: FileKind = FileKind.of(name, isDirectory)
    val baseName: String get() = if (isDirectory || extension.isEmpty()) name else name.dropLast(extension.length + 1)
    val isInArchive: Boolean get() = archivePath != null
    val file: File get() = File(path)
    val key: String get() = if (archivePath != null) "$archivePath!/$path" else path

    val permissionString: String
        get() = if (mode < 0) "" else Permissions.toString(mode, isDirectory, isSymlink)
}

@Serializable
sealed interface Location {
    val title: String
    val displayPath: String
    val parent: Location?
    val key: String

    @Serializable
    data class Local(val path: String) : Location {
        override val title: String get() = if (path == "/") "/" else path.substringAfterLast('/')
        override val displayPath: String get() = path
        override val parent: Location? get() = File(path).parent?.let { Local(it) }
        override val key: String get() = path
        fun child(name: String) = Local(if (path == "/") "/$name" else "$path/$name")
    }

    @Serializable
    data class Archive(val archivePath: String, val inner: String = "") : Location {
        override val title: String get() = if (inner.isEmpty()) archivePath.substringAfterLast('/') else inner.substringAfterLast('/')
        override val displayPath: String get() = if (inner.isEmpty()) archivePath else "$archivePath/$inner"
        override val parent: Location
            get() = if (inner.isEmpty()) Local(File(archivePath).parent ?: "/")
            else Archive(archivePath, inner.substringBeforeLast('/', ""))
        override val key: String get() = "$archivePath!/$inner"
        fun child(name: String) = Archive(archivePath, if (inner.isEmpty()) name else "$inner/$name")
    }
}

/** Глубина пути — нужна для выбора направления анимации при смене папки. */
val Location.depth: Int
    get() = when (this) {
        is Location.Local -> path.count { it == '/' }
        is Location.Archive -> archivePath.count { it == '/' } + 1 + if (inner.isEmpty()) 0 else inner.count { it == '/' } + 1
    }

fun Location.isAncestorOf(other: Location): Boolean {
    var p: Location? = other.parent
    while (p != null) {
        if (p == this) return true
        p = p.parent
    }
    return false
}

object Permissions {
    fun toString(mode: Int, isDirectory: Boolean, isSymlink: Boolean): String {
        val sb = StringBuilder(10)
        sb.append(
            when {
                isSymlink -> 'l'
                isDirectory -> 'd'
                else -> '-'
            }
        )
        val chars = "rwxrwxrwx"
        for (i in 0 until 9) {
            val bit = 1 shl (8 - i)
            sb.append(if (mode and bit != 0) chars[i] else '-')
        }
        if (mode and 0x800 != 0) sb[3] = if (sb[3] == 'x') 's' else 'S'
        if (mode and 0x400 != 0) sb[6] = if (sb[6] == 'x') 's' else 'S'
        if (mode and 0x200 != 0) sb[9] = if (sb[9] == 'x') 't' else 'T'
        return sb.toString()
    }

    fun toOctal(mode: Int): String = (mode and 0xFFF).toString(8).padStart(4, '0')
}
