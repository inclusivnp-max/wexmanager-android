package client.wexmanager.core.ops

import androidx.compose.runtime.Immutable
import client.wexmanager.core.fs.LocalFileSystem
import client.wexmanager.core.fs.StorageRepository
import client.wexmanager.core.settings.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Корзина: файлы переносятся (rename, без копирования) в скрытую папку `.WexTrash`
 * в корне того же тома, рядом хранится json с исходным путём.
 */
class TrashManager(private val storage: StorageRepository) {

    @Immutable
    @Serializable
    data class TrashEntry(
        val id: String,
        val name: String,
        val originalPath: String,
        val deletedAt: Long,
        val isDirectory: Boolean,
        val size: Long,
        val trashRoot: String = "",
    ) {
        val storedFile: File get() = File(trashRoot, "files/$id")
    }

    private fun rootFor(path: String): String? {
        val vol = storage.roots().filter { path.startsWith(it.path + "/") }.maxByOrNull { it.path.length } ?: return null
        return "${vol.path}/$TRASH_DIR"
    }

    /** Переносит в корзину; возвращает запись (для «Отменить») или null, если не получилось. */
    fun moveToTrash(file: File, transfer: ((src: File, dest: File) -> Boolean)? = null): TrashEntry? {
        val root = rootFor(file.absolutePath) ?: return null
        if (file.absolutePath.startsWith("$root/") || file.absolutePath == root) return null
        val filesDir = File(root, "files").apply { mkdirs() }
        val infoDir = File(root, "info").apply { mkdirs() }
        File(root, ".nomedia").takeIf { !it.exists() }?.createNewFile()
        val id = UUID.randomUUID().toString()
        val isDir = file.isDirectory
        val size = if (isDir) 0L else file.length()
        val target = File(filesDir, id)
        val info = File(infoDir, "$id.json")
        // Сначала метаданные, потом перенос: так файл никогда не «потеряется» в корзине без записи.
        val entry = TrashEntry(id, file.name, file.absolutePath, System.currentTimeMillis(), isDir, size)
        info.writeText(AppJson.encodeToString(TrashEntry.serializer(), entry))
        val moved = try {
            if (transfer != null) transfer(file, target) else file.renameTo(target)
        } catch (e: Exception) {
            info.delete()
            if (target.exists()) LocalFileSystem.deleteTree(target)
            throw e
        }
        if (!moved) {
            info.delete()
            if (target.exists()) LocalFileSystem.deleteTree(target)
            return null
        }
        return entry.copy(trashRoot = root)
    }

    suspend fun entries(): List<TrashEntry> = withContext(Dispatchers.IO) {
        storage.volumes().flatMap { vol ->
            val root = "${vol.path}/$TRASH_DIR"
            val infos = File(root, "info").listFiles() ?: return@flatMap emptyList()
            infos.mapNotNull { f ->
                runCatching { AppJson.decodeFromString(TrashEntry.serializer(), f.readText()) }.getOrNull()
                    ?.copy(trashRoot = root)
                    ?.takeIf { it.storedFile.exists() }
            }
        }.sortedByDescending { it.deletedAt }
    }

    /** Возвращает путь, куда восстановлен файл. */
    fun restore(entry: TrashEntry): String {
        val original = File(entry.originalPath)
        original.parentFile?.mkdirs()
        val target = if (original.exists()) {
            File(original.parentFile, LocalFileSystem.uniqueName(original.parent!!, original.name))
        } else original
        if (!entry.storedFile.renameTo(target)) throw IOException("Не удалось восстановить ${entry.name}")
        File(entry.trashRoot, "info/${entry.id}.json").delete()
        return target.path
    }

    /** Удаляет навсегда; если удалить удалось не всё — запись остаётся в корзине и бросается ошибка. */
    fun purge(entry: TrashEntry) {
        val ok = LocalFileSystem.deleteTree(entry.storedFile)
        if (!ok && java.nio.file.Files.exists(entry.storedFile.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw java.io.IOException("удалено не полностью")
        }
        File(entry.trashRoot, "info/${entry.id}.json").delete()
    }

    companion object {
        const val TRASH_DIR = ".WexTrash"
    }
}
