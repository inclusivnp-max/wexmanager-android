package client.wexmanager.core.fs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.coroutineContext

/**
 * Доступ к `Android/data` и `Android/obb` через выданное пользователем дерево SAF.
 * С Android 11 прямой `File.list` видит в этих папках только каталог самого приложения.
 * Вызов [requestIntent] открывает системный выбор сразу в нужной папке: DocumentsUI
 * отдаёт `.../root/<том>`, и этот сегмент заменяется на `.../document/<том>%3AAndroid%2Fdata`,
 * иначе кнопка «использовать» для этих каталогов заблокирована.
 */
object SafAccess {
    private const val AUTHORITY = "com.android.externalstorage.documents"
    private val PROJECTION = arrayOf(
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )

    data class Grant(val uri: Uri, val volumeId: String, val volumeRoot: String, val relative: String)

    fun isRestricted(path: String): Boolean {
        val (_, root) = volumeOf(path) ?: return false
        return SafPaths.restrictedRelative(root, path) != null
    }

    fun restrictedRoot(path: String): String? {
        val (_, root) = volumeOf(path) ?: return null
        return SafPaths.restrictedRoot(root, path)
    }

    /**
     * Папку нельзя честно прочитать через [File]: нет выдачи не считается.
     * Корень `Android/data` всегда идёт через SAF, даже если `list()` вернул свою папку приложения.
     */
    fun needsSaf(context: Context, path: String): Boolean {
        if (!isRestricted(path) || bestGrant(context, path) == null) return false
        val root = restrictedRoot(path) ?: return false
        if (SafPaths.normalize(path) == SafPaths.normalize(root)) return true
        val f = File(path)
        if (f.exists()) return !(f.canRead() && (!f.isDirectory || f.list() != null))
        val parent = f.parentFile ?: return true
        if (SafPaths.normalize(parent.path) == SafPaths.normalize(root)) return true
        return !(parent.canRead() && parent.canWrite())
    }

    fun bestGrant(context: Context, path: String): Grant? {
        val (volumeId, volumeRoot) = volumeOf(path) ?: return null
        return grants(context)
            .filter { it.volumeId == volumeId && it.volumeRoot == volumeRoot && SafPaths.grantCovers(volumeRoot, it.relative, path) }
            .maxByOrNull { it.relative.length }
    }

    fun grants(context: Context): List<Grant> =
        context.contentResolver.persistedUriPermissions.mapNotNull { perm ->
            if (!perm.isReadPermission) return@mapNotNull null
            val uri = perm.uri
            if (uri.authority != AUTHORITY) return@mapNotNull null
            val tree = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return@mapNotNull null
            val colon = tree.indexOf(':')
            if (colon <= 0) return@mapNotNull null
            val volumeId = tree.substring(0, colon)
            val relative = tree.substring(colon + 1).trim('/')
            val root = volumeRoot(volumeId) ?: return@mapNotNull null
            Grant(uri, volumeId, SafPaths.normalize(root), relative)
        }

    fun requestIntent(context: Context, path: String): Intent? {
        val target = restrictedRoot(path) ?: return null
        val (volumeId, volumeRoot) = volumeOf(target) ?: return null
        val relative = SafPaths.relative(volumeRoot, target)?.replace("/", "%2F") ?: return null
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            volume(context, volumeRoot)?.createOpenDocumentTreeIntent()
                ?: Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        } else {
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        }
        @Suppress("DEPRECATION")
        val initial = intent.getParcelableExtra<Uri>(DocumentsContract.EXTRA_INITIAL_URI)
        val rewritten = rewriteInitial(initial?.toString(), volumeId, relative)
        if (rewritten != null) intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(rewritten))
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
        )
        return intent
    }

    fun list(context: Context, path: String): List<FileItem> {
        val grant = bestGrant(context, path) ?: throw AccessDeniedException(path, grantable = isRestricted(path))
        val docId = documentId(path) ?: throw AccessDeniedException(path, grantable = true)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(grant.uri, docId)
        val cursor = try {
            context.contentResolver.query(children, PROJECTION, null, null, null)
        } catch (e: SecurityException) {
            throw AccessDeniedException(path, grantable = true)
        } ?: throw AccessDeniedException(path, grantable = true)
        return cursor.use { c ->
            val nameI = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeI = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeI = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val modI = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val out = ArrayList<FileItem>(c.count.coerceAtLeast(0))
            val base = SafPaths.normalize(path)
            while (c.moveToNext()) {
                if (nameI < 0) continue
                val name = c.getString(nameI) ?: continue
                if (name.isEmpty() || name == "." || name == ".." || '/' in name || '\u0000' in name) continue
                val dir = mimeI >= 0 && c.getString(mimeI) == DocumentsContract.Document.MIME_TYPE_DIR
                out += FileItem(
                    path = "$base/$name",
                    name = name,
                    isDirectory = dir,
                    size = if (dir || sizeI < 0) 0L else c.getLong(sizeI),
                    lastModified = if (modI < 0) 0L else c.getLong(modI),
                )
            }
            out
        }
    }

    fun stat(context: Context, path: String): FileItem? {
        val grant = bestGrant(context, path) ?: return null
        val docId = documentId(path) ?: return null
        val uri = DocumentsContract.buildDocumentUriUsingTree(grant.uri, docId)
        val cursor = runCatching { context.contentResolver.query(uri, PROJECTION, null, null, null) }.getOrNull() ?: return null
        return cursor.use { c ->
            if (!c.moveToFirst()) return null
            val nameI = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeI = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeI = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val modI = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val name = if (nameI >= 0) c.getString(nameI) else null
            val dir = mimeI >= 0 && c.getString(mimeI) == DocumentsContract.Document.MIME_TYPE_DIR
            FileItem(
                path = SafPaths.normalize(path),
                name = name ?: path.substringAfterLast('/'),
                isDirectory = dir,
                size = if (dir || sizeI < 0) 0L else c.getLong(sizeI),
                lastModified = if (modI < 0) 0L else c.getLong(modI),
            )
        }
    }

    fun mkdir(context: Context, path: String): Boolean {
        val parent = File(path).parent ?: return false
        val name = File(path).name
        if (name.isEmpty() || '/' in name) return false
        val grant = bestGrant(context, parent) ?: return false
        val parentId = documentId(parent) ?: return false
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(grant.uri, parentId)
        return runCatching {
            DocumentsContract.createDocument(context.contentResolver, parentUri, DocumentsContract.Document.MIME_TYPE_DIR, name) != null
        }.getOrDefault(false)
    }

    fun createFile(context: Context, path: String): Boolean {
        if (stat(context, path) != null) return true
        val parent = File(path).parent ?: return false
        val name = File(path).name
        val grant = bestGrant(context, parent) ?: return false
        val parentId = documentId(parent) ?: return false
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(grant.uri, parentId)
        return runCatching {
            DocumentsContract.createDocument(context.contentResolver, parentUri, "application/octet-stream", name) != null
        }.getOrDefault(false)
    }

    fun delete(context: Context, path: String): Boolean {
        val grant = bestGrant(context, path) ?: return false
        val docId = documentId(path) ?: return false
        val uri = DocumentsContract.buildDocumentUriUsingTree(grant.uri, docId)
        return runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }.getOrDefault(false)
    }

    fun rename(context: Context, path: String, newName: String): Boolean {
        if (newName.isEmpty() || '/' in newName || newName == "." || newName == "..") return false
        val grant = bestGrant(context, path) ?: return false
        val docId = documentId(path) ?: return false
        val uri = DocumentsContract.buildDocumentUriUsingTree(grant.uri, docId)
        return runCatching { DocumentsContract.renameDocument(context.contentResolver, uri, newName) != null }.getOrDefault(false)
    }

    fun openInput(context: Context, path: String): InputStream {
        val uri = documentUri(context, path) ?: throw IOException("Нет доступа: ${File(path).name}")
        return context.contentResolver.openInputStream(uri) ?: throw IOException("Не удалось открыть ${File(path).name}")
    }

    /** Создаёт документ, если его ещё нет, и открывает на запись с обрезкой. */
    fun openOutput(context: Context, path: String): OutputStream {
        if (stat(context, path) == null && !createFile(context, path)) throw IOException("Не удалось создать ${File(path).name}")
        val uri = documentUri(context, path) ?: throw IOException("Нет доступа: ${File(path).name}")
        return context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Не удалось записать ${File(path).name}")
    }

    /** Копия дерева из выданной папки в обычный файл. [onBytes] зовётся по мере чтения. */
    fun copyTreeBlocking(context: Context, srcPath: String, dest: File, onBytes: (Long) -> Unit = {}) {
        val item = stat(context, srcPath) ?: throw IOException("Нет файла ${File(srcPath).name}")
        if (item.isDirectory) {
            if (!dest.isDirectory && !dest.mkdirs()) throw IOException("Не удалось создать папку ${dest.name}")
            for (child in list(context, srcPath)) copyTreeBlocking(context, child.path, File(dest, child.name), onBytes)
        } else {
            dest.parentFile?.mkdirs()
            openInput(context, srcPath).use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        if (n > 0) onBytes(n.toLong())
                    }
                }
            }
        }
    }

    suspend fun pump(input: InputStream, output: OutputStream, onBytes: (Long) -> Unit) {
        val buf = ByteArray(256 * 1024)
        while (true) {
            coroutineContext.ensureActive()
            val n = input.read(buf)
            if (n < 0) break
            output.write(buf, 0, n)
            if (n > 0) onBytes(n.toLong())
        }
    }

    private fun documentUri(context: Context, path: String): Uri? {
        val grant = bestGrant(context, path) ?: return null
        val docId = documentId(path) ?: return null
        return DocumentsContract.buildDocumentUriUsingTree(grant.uri, docId)
    }

    private fun documentId(path: String): String? {
        val (volumeId, volumeRoot) = volumeOf(path) ?: return null
        return SafPaths.documentId(volumeId, volumeRoot, path)
    }

    private fun volumeOf(path: String): Pair<String, String>? {
        val primary = SafPaths.normalize(Environment.getExternalStorageDirectory().absolutePath)
        val p = SafPaths.normalize(path)
        if (p == primary || p.startsWith("$primary/")) return "primary" to primary
        val marker = "/storage/"
        if (!p.startsWith(marker)) return null
        val rest = p.removePrefix(marker)
        val id = rest.substringBefore('/')
        if (id.isEmpty() || id == "emulated" || id == "self") return null
        val root = "/storage/$id"
        if (p != root && !p.startsWith("$root/")) return null
        return id to root
    }

    private fun volumeRoot(volumeId: String): String? =
        if (volumeId == "primary") Environment.getExternalStorageDirectory().absolutePath else "/storage/$volumeId"

    private fun volume(context: Context, volumeRoot: String): StorageVolume? {
        val sm = context.getSystemService(StorageManager::class.java) ?: return null
        return sm.storageVolumes.firstOrNull { volumePath(it) == SafPaths.normalize(volumeRoot) }
            ?: sm.primaryStorageVolume.takeIf { volumeRoot == SafPaths.normalize(Environment.getExternalStorageDirectory().absolutePath) }
    }

    private fun volumePath(v: StorageVolume): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return v.directory?.absolutePath?.let(SafPaths::normalize)
        return runCatching { StorageVolume::class.java.getMethod("getPath").invoke(v) as String }.getOrNull()?.let(SafPaths::normalize)
    }

    /** `.../root/primary` → `.../document/primary%3AAndroid%2Fdata`. */
    internal fun rewriteInitial(initial: String?, volumeId: String, encodedRelative: String): String? {
        if (initial.isNullOrBlank()) {
            return "content://$AUTHORITY/document/$volumeId%3A$encodedRelative"
        }
        var scheme = initial.replace("/root/", "/document/")
        scheme = if (scheme.contains("%3A")) {
            scheme.replace(Regex("%3A.*$"), "%3A$encodedRelative")
        } else {
            "$scheme%3A$encodedRelative"
        }
        return scheme
    }
}
