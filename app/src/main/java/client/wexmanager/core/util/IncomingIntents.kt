package client.wexmanager.core.util

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Превращает входящий VIEW/EDIT-интент в путь к локальному файлу. */
object IncomingIntents {

    const val ACTION_OPEN_PATH = "client.wexmanager.OPEN_PATH"
    const val EXTRA_PATH = "path"

    fun isOpenIntent(intent: Intent?): Boolean = intent != null && (
        ((intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_EDIT) && intent.data != null) ||
            (intent.action == ACTION_OPEN_PATH && intent.getStringExtra(EXTRA_PATH) != null)
        )

    /** Результат разбора: локальный путь и исходный content:// (если работаем с копией). */
    data class Resolved(val path: String, val sourceUri: String?)

    /** Путь из интента: ярлык (extra) или VIEW/EDIT (uri). */
    suspend fun resolve(context: Context, intent: Intent): Resolved? {
        intent.getStringExtra(EXTRA_PATH)?.takeIf { intent.action == ACTION_OPEN_PATH }?.let { return Resolved(it, null) }
        val uri = intent.data ?: return null
        return withContext(Dispatchers.IO) {
            when (uri.scheme) {
                ContentResolver.SCHEME_FILE, null -> uri.path?.let { Resolved(runCatching { File(it).canonicalPath }.getOrDefault(it), null) }
                ContentResolver.SCHEME_CONTENT -> directPath(context, uri)?.let { Resolved(it, null) }
                    ?: copyToCache(context, uri)?.let { Resolved(it, uri.toString()) }
                else -> null
            }
        }
    }

    suspend fun resolvePath(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> uri.path?.let { runCatching { File(it).canonicalPath }.getOrDefault(it) }
            ContentResolver.SCHEME_CONTENT -> directPath(context, uri) ?: copyToCache(context, uri)
            null -> uri.path
            else -> null
        }
    }

    /** Пытается получить настоящий путь (ExternalStorageProvider, file-provider с root-path и т.п.). */
    private fun directPath(context: Context, uri: Uri): String? {
        runCatching {
            if (DocumentsContract.isDocumentUri(context, uri) && uri.authority == "com.android.externalstorage.documents") {
                val id = DocumentsContract.getDocumentId(uri)
                val (type, rel) = id.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                val base = if (type.equals("primary", true)) Environment.getExternalStorageDirectory().path else "/storage/$type"
                val f = File(base, rel)
                if (f.exists()) return f.path
            }
        }
        // Многие файловые менеджеры кладут абсолютный путь в последний сегмент или в path.
        val p = uri.path ?: return null
        listOf(p, p.substringAfter("/root", ""), p.substringAfter("/external_files", "").let { if (it.isEmpty()) "" else Environment.getExternalStorageDirectory().path + it })
            .filter { it.startsWith("/storage/") }
            .map { File(it) }
            .firstOrNull { it.isFile && it.canRead() && it.canonicalPath.startsWith("/storage/") }
            ?.let { return it.path }
        return null
    }

    private fun copyToCache(context: Context, uri: Uri): String? = runCatching {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        val incoming = File(context.cacheDir, "incoming")
        // Старые копии (старше суток) удаляем, чтобы кеш не рос бесконечно.
        incoming.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.deleteRecursively() }
        val dir = File(incoming, "${System.currentTimeMillis()}").apply { mkdirs() }
        val target = File(dir, name.replace('/', '_'))
        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } } ?: return null
        target.path
    }.getOrNull()
}
