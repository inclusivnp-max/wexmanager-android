package client.wexmanager.core.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import client.wexmanager.core.archive.ArchiveEngine
import client.wexmanager.core.fs.FileItem
import client.wexmanager.core.fs.FileKind
import client.wexmanager.core.fs.Location
import client.wexmanager.core.settings.AppSettings
import client.wexmanager.ui.navigation.Route
import java.io.File

sealed interface OpenAction {
    data class Navigate(val route: Route) : OpenAction
    data class Enter(val location: Location) : OpenAction
    data class External(val path: String) : OpenAction
    /** [archive]/[entry] — файл извлечён из архива (для правки с перепаковкой). */
    data class ChooseOpenAs(val item: FileItem, val archive: String? = null, val entry: String? = null) : OpenAction
}

enum class OpenAs(val title: String) {
    Text("Текст"),
    Image("Изображение"),
    Media("Аудио / видео"),
    Hex("HEX-просмотр"),
    Pdf("PDF-документ"),
    Preview("Предпросмотр (Markdown, HTML, CSV)"),
    Database("База данных SQLite"),
    Archive("Архив"),
    Apk("Приложение (APK)"),
    External("Другое приложение"),
}

object FileOpener {

    /** Запомненный пользователем способ открытия по расширению (зеркало UserData.openWith). */
    @Volatile
    var openWith: Map<String, String> = emptyMap()

    private fun path(item: FileItem) = item.path

    fun rememberedMode(extension: String): OpenAs? =
        openWith[extension.lowercase()]?.let { name -> OpenAs.entries.firstOrNull { it.name == name } }

    fun actionFor(item: FileItem, settings: AppSettings): OpenAction {
        if (item.isDirectory) {
            return OpenAction.Enter(Location.Local(item.path))
        }
        if (item.extension.isNotEmpty()) rememberedMode(item.extension)?.let { mode ->
            // Запомненный режим, который к этому файлу не подходит (например «Архив» для текста), пропускаем.
            val fits = when (mode) {
                OpenAs.Archive -> ArchiveEngine.isSupported(File(path(item)))
                OpenAs.Apk -> ArchiveEngine.detect(File(path(item))) == client.wexmanager.core.archive.ArchiveType.Zip
                OpenAs.Database -> client.wexmanager.core.db.SqliteBrowser.isSqlite(File(path(item)))
                else -> true
            }
            if (fits) return openAs(item, mode)
        }
        val path = item.path
        return when (item.kind) {
            FileKind.Apk -> if (item.extension == "apk") OpenAction.Navigate(Route.ApkInfo(path))
            else if (client.wexmanager.core.apk.SplitInstaller.isBundle(item.name)) OpenAction.Navigate(Route.SplitInstall(path))
            else if (settings.openArchivesAsFolders) OpenAction.Enter(Location.Archive(path)) else OpenAction.External(path)
            FileKind.Archive, FileKind.Ebook, FileKind.Document, FileKind.Spreadsheet, FileKind.Presentation -> {
                val archiveLike = item.kind == FileKind.Archive || item.extension in setOf("epub", "cbz", "docx", "xlsx", "pptx", "odt")
                when {
                    item.kind == FileKind.Archive && settings.openArchivesAsFolders && ArchiveEngine.isSupported(File(path)) ->
                        OpenAction.Enter(Location.Archive(path))
                    archiveLike && item.kind == FileKind.Archive -> OpenAction.ChooseOpenAs(item)
                    else -> OpenAction.External(path)
                }
            }
            FileKind.Image -> OpenAction.Navigate(Route.ImageViewer(path))
            FileKind.Vector -> OpenAction.Navigate(Route.ImageViewer(path))
            FileKind.Video, FileKind.Audio -> OpenAction.Navigate(Route.MediaPlayer(path))
            FileKind.Dex -> if (item.extension == "dex") OpenAction.Navigate(Route.DexEditor(path)) else OpenAction.Navigate(Route.HexViewer(path))
            FileKind.Database -> if (client.wexmanager.core.db.SqliteBrowser.isSqlite(File(path))) OpenAction.Navigate(Route.DatabaseViewer(path)) else OpenAction.Navigate(Route.HexViewer(path))
            FileKind.Executable, FileKind.Library -> if (client.wexmanager.core.elf.Elf.isElf(File(path))) OpenAction.Navigate(Route.ElfViewer(path)) else OpenAction.Navigate(Route.HexViewer(path))
            FileKind.DiskImage -> OpenAction.Navigate(Route.HexViewer(path))
            FileKind.Pdf -> OpenAction.Navigate(Route.PdfViewer(path))
            FileKind.Font -> if (item.extension in setOf("ttf", "otf", "ttc")) OpenAction.Navigate(Route.FontViewer(path)) else OpenAction.External(path)
            FileKind.Torrent -> OpenAction.External(path)
            FileKind.Unknown -> when {
                item.extension == "arsc" -> OpenAction.Navigate(Route.ArscEditor(path))
                looksLikeText(File(path)) -> OpenAction.Navigate(Route.Editor(path))
                else -> OpenAction.ChooseOpenAs(item)
            }
            else -> if (item.kind.isTextLike) OpenAction.Navigate(Route.Editor(path)) else OpenAction.ChooseOpenAs(item)
        }
    }

    fun openAs(item: FileItem, mode: OpenAs): OpenAction = when (mode) {
        OpenAs.Text -> OpenAction.Navigate(Route.Editor(item.path))
        OpenAs.Image -> OpenAction.Navigate(Route.ImageViewer(item.path, siblings = false))
        OpenAs.Media -> OpenAction.Navigate(Route.MediaPlayer(item.path))
        OpenAs.Hex -> OpenAction.Navigate(Route.HexViewer(item.path))
        OpenAs.Pdf -> OpenAction.Navigate(Route.PdfViewer(item.path))
        OpenAs.Preview -> OpenAction.Navigate(Route.Preview(item.path))
        OpenAs.Database -> OpenAction.Navigate(Route.DatabaseViewer(item.path))
        OpenAs.Archive -> OpenAction.Enter(Location.Archive(item.path))
        OpenAs.Apk -> OpenAction.Navigate(Route.ApkInfo(item.path))
        OpenAs.External -> OpenAction.External(item.path)
    }

    /** Эвристика: первые 8 КБ без нулевых байтов и с малой долей управляющих символов. */
    fun looksLikeText(file: File): Boolean {
        if (file.length() == 0L) return true
        return runCatching {
            file.inputStream().use { s ->
                val buf = ByteArray(8192)
                val n = s.read(buf)
                if (n <= 0) return true
                var control = 0
                for (i in 0 until n) {
                    val b = buf[i].toInt() and 0xFF
                    if (b == 0) return false
                    if (b < 0x09 || (b in 0x0E..0x1F)) control++
                }
                control * 100 / n < 5
            }
        }.getOrDefault(false)
    }

    fun mimeType(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
        return when (FileKind.of(name, false)) {
            FileKind.Apk -> "application/vnd.android.package-archive"
            FileKind.Image -> "image/*"
            FileKind.Video -> "video/*"
            FileKind.Audio -> "audio/*"
            FileKind.Text, FileKind.Code, FileKind.Markup, FileKind.Data, FileKind.Config, FileKind.Script, FileKind.Log -> "text/plain"
            FileKind.Pdf -> "application/pdf"
            FileKind.Archive -> "application/zip"
            else -> "*/*"
        }
    }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    fun openExternal(context: Context, file: File, mime: String = mimeType(file.name), chooser: Boolean = true) {
        val uri = runCatching { uriFor(context, file) }.getOrElse {
            Toast.makeText(context, "Не удалось открыть: ${it.message}", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(if (chooser) Intent.createChooser(intent, "Открыть с помощью").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) else intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Нет приложения для открытия этого файла", Toast.LENGTH_SHORT).show()
        }
    }

    fun share(context: Context, files: List<File>) {
        if (files.isEmpty()) return
        val uris = files.mapNotNull { runCatching { uriFor(context, it) }.getOrNull() }
        val mime = files.map { mimeType(it.name) }.distinct().singleOrNull() ?: "*/*"
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0]).setType(mime)
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris)).setType(mime)
        }
        intent.clipData = ClipData.newRawUri(null, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Поделиться").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Ярлык на рабочем столе: открывает папку (или файл) в WexManager. */
    fun pinShortcut(context: Context, path: String, title: String, isDirectory: Boolean): Boolean {
        if (!androidx.core.content.pm.ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        val intent = Intent(IncomingIntents.ACTION_OPEN_PATH)
            .putExtra(IncomingIntents.EXTRA_PATH, path)
            .setClassName(context, "client.wexmanager.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val shortcut = androidx.core.content.pm.ShortcutInfoCompat.Builder(context, "path:$path")
            .setShortLabel(title.take(24).ifEmpty { "/" })
            .setLongLabel(path)
            .setIcon(androidx.core.graphics.drawable.IconCompat.createWithResource(context, client.wexmanager.R.mipmap.ic_launcher))
            .setIntent(intent)
            .build()
        return androidx.core.content.pm.ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    fun installApk(context: Context, file: File) {
        val uri = uriFor(context, file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Установщик недоступен", Toast.LENGTH_SHORT).show()
        }
    }
}
