package client.wexmanager.core.fs

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class AccessDeniedException(path: String, val grantable: Boolean = false) : IOException("Нет доступа: $path")
class NotFoundException(path: String) : IOException("Не найдено: $path")

object LocalFileSystem {
    /** Application context для SAF. Ставится из [client.wexmanager.AppContainer]. */
    @Volatile
    var appContext: android.content.Context? = null

    /**
     * Рекурсивное удаление без перехода по символическим ссылкам: ссылка удаляется сама,
     * а то, на что она указывает, не трогается (File.deleteRecursively заходит внутрь ссылок).
     */
    fun deleteTree(f: File): Boolean {
        val link = java.nio.file.Files.isSymbolicLink(f.toPath())
        var ok = true
        if (!link && f.isDirectory) f.listFiles()?.forEach { ok = deleteTree(it) && ok }
        return (f.delete() || !(link || f.exists())) && ok
    }

    suspend fun list(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        val app = appContext
        // Чужие Android/data и Android/obb: прямой список неполный или пустой, если папка выдана через SAF.
        if (app != null && SafAccess.needsSaf(app, path)) return@withContext SafAccess.list(app, path)
        val dir = File(path)
        if (!dir.exists() && !RootShell.enabled) {
            if (SafAccess.isRestricted(path)) throw AccessDeniedException(path, grantable = true)
            throw NotFoundException(path)
        }
        val names = dir.list() ?: run {
            // /storage/emulated не читается, но в нём всегда есть основная память пользователя.
            if (path == "/storage/emulated" && File("/storage/emulated/0").isDirectory) arrayOf("0")
            // Закрытая папка (/data, системные разделы) — через root, если он включён.
            else if (RootShell.enabled) return@withContext RootShell.list(path)
            else throw AccessDeniedException(path, grantable = SafAccess.isRestricted(path))
        }
        val result = ArrayList<FileItem>(names.size)
        for ((i, name) in names.withIndex()) {
            if (i and 255 == 0) ensureActive()
            result += stat(if (path == "/") "/$name" else "$path/$name", name)
        }
        result
    }

    fun stat(path: String, name: String = path.substringAfterLast('/').ifEmpty { "/" }): FileItem {
        return try {
            val st = Os.lstat(path)
            val isLink = OsConstants.S_ISLNK(st.st_mode)
            if (isLink) {
                val target = runCatching { Os.readlink(path) }.getOrNull()
                val resolved = runCatching { Os.stat(path) }.getOrNull()
                FileItem(
                    path = path,
                    name = name,
                    isDirectory = resolved?.let { OsConstants.S_ISDIR(it.st_mode) } ?: false,
                    size = resolved?.st_size ?: 0L,
                    lastModified = st.st_mtime * 1000L,
                    isSymlink = true,
                    mode = st.st_mode,
                    linkTarget = target,
                    uid = st.st_uid,
                    gid = st.st_gid,
                )
            } else {
                FileItem(
                    path = path,
                    name = name,
                    isDirectory = OsConstants.S_ISDIR(st.st_mode),
                    size = st.st_size,
                    lastModified = st.st_mtime * 1000L,
                    mode = st.st_mode,
                    uid = st.st_uid,
                    gid = st.st_gid,
                )
            }
        } catch (_: ErrnoException) {
            val f = File(path)
            FileItem(
                path = path,
                name = name,
                isDirectory = f.isDirectory,
                size = if (f.isFile) f.length() else 0L,
                lastModified = f.lastModified(),
            )
        }
    }

    fun countChildren(path: String, includeHidden: Boolean): Int {
        val app = appContext
        if (app != null && SafAccess.needsSaf(app, path)) {
            val names = runCatching { SafAccess.list(app, path).map { it.name } }.getOrNull() ?: return -1
            return if (includeHidden) names.size else names.count { !it.startsWith('.') }
        }
        val names = File(path).list() ?: return -1
        return if (includeHidden) names.size else names.count { !it.startsWith('.') }
    }


    fun exists(path: String): Boolean = File(path).exists()

    fun uniqueName(dir: String, name: String): String {
        if (!File(dir, name).exists()) return name
        val dot = name.lastIndexOf('.')
        val (base, ext) = if (dot > 0) name.substring(0, dot) to name.substring(dot) else name to ""
        var i = 1
        while (true) {
            val candidate = "$base ($i)$ext"
            if (!File(dir, candidate).exists()) return candidate
            i++
        }
    }

    fun chmod(path: String, mode: Int) = Os.chmod(path, mode)

    /** Размер содержимого папки (рекурсивно) с учётом отмены. */
    suspend fun directorySize(path: String, onProgress: (files: Int, dirs: Int, bytes: Long) -> Unit = { _, _, _ -> }): Triple<Int, Int, Long> =
        withContext(Dispatchers.IO) {
            var files = 0
            var dirs = 0
            var bytes = 0L
            val stack = ArrayDeque<File>()
            stack.add(File(path))
            var tick = 0
            while (stack.isNotEmpty()) {
                val dir = stack.removeLast()
                val children = dir.listFiles() ?: continue
                for (c in children) {
                    if (++tick and 511 == 0) {
                        ensureActive()
                        onProgress(files, dirs, bytes)
                    }
                    val isLink = runCatching { OsConstants.S_ISLNK(Os.lstat(c.path).st_mode) }.getOrDefault(false)
                    if (c.isDirectory && !isLink) {
                        dirs++
                        stack.add(c)
                    } else {
                        files++
                        bytes += c.length()
                    }
                }
            }
            onProgress(files, dirs, bytes)
            Triple(files, dirs, bytes)
        }
}
