package client.wexmanager.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.Subject
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Difference
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Html
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.TextSnippet
import androidx.compose.material.icons.rounded.Toll
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Javascript
import androidx.compose.material.icons.rounded.Css
import androidx.compose.material.icons.rounded.Php
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Dataset
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Token
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.RawOn
import androidx.compose.material.icons.rounded.Polyline
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import client.wexmanager.core.fs.FileKind

/** Набор глифов, доступных для пользовательских иконок (ключ → вектор). */
object FileGlyphs {
    val all: Map<String, ImageVector> = linkedMapOf(
        "file" to Icons.AutoMirrored.Rounded.InsertDriveFile,
        "folder" to Icons.Rounded.Folder,
        "folder_special" to Icons.Rounded.FolderSpecial,
        "android" to Icons.Rounded.Android,
        "archive" to Icons.Rounded.FolderZip,
        "box" to Icons.Rounded.Inventory2,
        "image" to Icons.Rounded.Image,
        "photo" to Icons.Rounded.Photo,
        "gif" to Icons.Rounded.Gif,
        "raw" to Icons.Rounded.RawOn,
        "vector" to Icons.Rounded.Polyline,
        "brush" to Icons.Rounded.Brush,
        "palette" to Icons.Rounded.Palette,
        "video" to Icons.Rounded.VideoFile,
        "movie" to Icons.Rounded.Movie,
        "animation" to Icons.Rounded.Animation,
        "audio" to Icons.Rounded.AudioFile,
        "music" to Icons.Rounded.MusicNote,
        "album" to Icons.Rounded.Album,
        "text" to Icons.Rounded.TextSnippet,
        "article" to Icons.AutoMirrored.Rounded.Article,
        "subject" to Icons.AutoMirrored.Rounded.Subject,
        "code" to Icons.Rounded.Code,
        "javascript" to Icons.Rounded.Javascript,
        "css" to Icons.Rounded.Css,
        "php" to Icons.Rounded.Php,
        "html" to Icons.Rounded.Html,
        "java" to Icons.Rounded.Coffee,
        "data" to Icons.Rounded.DataObject,
        "dataset" to Icons.Rounded.Dataset,
        "web" to Icons.Rounded.Language,
        "settings" to Icons.Rounded.Settings,
        "terminal" to Icons.Rounded.Terminal,
        "pdf" to Icons.Rounded.PictureAsPdf,
        "document" to Icons.Rounded.Description,
        "table" to Icons.Rounded.TableChart,
        "slides" to Icons.Rounded.Slideshow,
        "book" to Icons.Rounded.Book,
        "font" to Icons.Rounded.FontDownload,
        "database" to Icons.Rounded.Storage,
        "memory" to Icons.Rounded.Memory,
        "chip" to Icons.Rounded.DeveloperBoard,
        "token" to Icons.Rounded.Token,
        "extension" to Icons.Rounded.Extension,
        "key" to Icons.Rounded.Key,
        "lock" to Icons.Rounded.Lock,
        "fingerprint" to Icons.Rounded.Fingerprint,
        "disk" to Icons.Rounded.Save,
        "subtitles" to Icons.Rounded.Subtitles,
        "torrent" to Icons.Rounded.Toll,
        "contact" to Icons.Rounded.Contacts,
        "badge" to Icons.Rounded.Badge,
        "calendar" to Icons.Rounded.Event,
        "log" to Icons.Rounded.Gesture,
        "diff" to Icons.Rounded.Difference,
        "bug" to Icons.Rounded.BugReport,
        "widgets" to Icons.Rounded.Widgets,
        "apps" to Icons.Rounded.Apps,
        "ar" to Icons.Rounded.ViewInAr,
        "download" to Icons.Rounded.Download,
        "camera" to Icons.Rounded.PhotoCamera,
        "library_music" to Icons.Rounded.LibraryMusic,
        "video_library" to Icons.Rounded.VideoLibrary,
        "star" to Icons.Rounded.Star,
        "favorite" to Icons.Rounded.Favorite,
        "work" to Icons.Rounded.Work,
        "cloud" to Icons.Rounded.Cloud,
        "bolt" to Icons.Rounded.Bolt,
        "science" to Icons.Rounded.Science,
        "games" to Icons.Rounded.SportsEsports,
        "school" to Icons.Rounded.School,
        "map" to Icons.Rounded.Map,
    )

    fun get(key: String?): ImageVector? = key?.let { all[it] }

    /** Глиф по умолчанию с учётом конкретного расширения. */
    fun forFile(kind: FileKind, ext: String): ImageVector = when (ext) {
        "js", "mjs", "cjs", "jsx" -> Icons.Rounded.Javascript
        "css", "scss", "sass", "less" -> Icons.Rounded.Css
        "php" -> Icons.Rounded.Php
        "html", "htm", "xhtml" -> Icons.Rounded.Html
        "java", "class", "jar" -> Icons.Rounded.Coffee
        "gif" -> Icons.Rounded.Gif
        "dng", "raw", "cr2", "nef" -> Icons.Rounded.RawOn
        "psd" -> Icons.Rounded.Brush
        "csv", "tsv" -> Icons.Rounded.Dataset
        "dex", "odex", "vdex", "oat" -> Icons.Rounded.Token
        "kts", "toml", "yaml" -> Icons.Rounded.Code
        else -> forKind(kind)
    }

    fun forKind(kind: FileKind): ImageVector = when (kind) {
        FileKind.Folder -> Icons.Rounded.Folder
        FileKind.Apk -> Icons.Rounded.Android
        FileKind.Archive -> Icons.Rounded.FolderZip
        FileKind.Image -> Icons.Rounded.Image
        FileKind.Vector -> Icons.Rounded.Polyline
        FileKind.Video -> Icons.Rounded.Movie
        FileKind.Audio -> Icons.Rounded.MusicNote
        FileKind.Text -> Icons.Rounded.TextSnippet
        FileKind.Code -> Icons.Rounded.Code
        FileKind.Markup -> Icons.Rounded.Code
        FileKind.Data -> Icons.Rounded.DataObject
        FileKind.Config -> Icons.Rounded.Settings
        FileKind.Pdf -> Icons.Rounded.PictureAsPdf
        FileKind.Document -> Icons.Rounded.Description
        FileKind.Spreadsheet -> Icons.Rounded.TableChart
        FileKind.Presentation -> Icons.Rounded.Slideshow
        FileKind.Ebook -> Icons.Rounded.Book
        FileKind.Font -> Icons.Rounded.FontDownload
        FileKind.Database -> Icons.Rounded.Storage
        FileKind.Executable -> Icons.Rounded.Memory
        FileKind.Script -> Icons.Rounded.Terminal
        FileKind.Dex -> Icons.Rounded.Token
        FileKind.Library -> Icons.Rounded.Extension
        FileKind.Certificate -> Icons.Rounded.Key
        FileKind.DiskImage -> Icons.Rounded.Save
        FileKind.Subtitle -> Icons.Rounded.Subtitles
        FileKind.Torrent -> Icons.Rounded.Toll
        FileKind.Contact -> Icons.Rounded.Contacts
        FileKind.Calendar -> Icons.Rounded.Event
        FileKind.Web -> Icons.Rounded.Language
        FileKind.Log -> Icons.AutoMirrored.Rounded.Subject
        FileKind.Patch -> Icons.Rounded.Difference
        FileKind.Unknown -> Icons.AutoMirrored.Rounded.InsertDriveFile
    }

    /** Базовые оттенки категорий — затем гармонизируются с primary темы. */
    fun colorFor(kind: FileKind, ext: String = ""): Color = when (kind) {
        FileKind.Folder -> Color(0xFF4F7CF0)
        FileKind.Apk -> Color(0xFF3DBE6B)
        FileKind.Archive -> Color(0xFFE2A93B)
        FileKind.Image, FileKind.Vector -> Color(0xFFE5538C)
        FileKind.Video -> Color(0xFFE5533D)
        FileKind.Audio -> Color(0xFFA55AE8)
        FileKind.Text, FileKind.Log -> Color(0xFF6B7A90)
        FileKind.Code -> when (ext) {
            "kt", "kts" -> Color(0xFF8F5CF6)
            "java", "class" -> Color(0xFFE07A2E)
            "py", "pyw" -> Color(0xFF3B7BD1)
            "js", "mjs", "cjs", "jsx" -> Color(0xFFD9B514)
            "ts", "tsx" -> Color(0xFF3178C6)
            "c", "h", "cpp", "hpp", "cc" -> Color(0xFF5C7FC2)
            "rs" -> Color(0xFFCE6B39)
            "go" -> Color(0xFF00ADD8)
            "smali" -> Color(0xFF8BAA2E)
            else -> Color(0xFF2E9AA8)
        }
        FileKind.Markup, FileKind.Web -> Color(0xFFE36C2E)
        FileKind.Data, FileKind.Config -> Color(0xFF5F8C3A)
        FileKind.Pdf -> Color(0xFFD93A3A)
        FileKind.Document -> Color(0xFF2F6BD8)
        FileKind.Spreadsheet -> Color(0xFF1F9D57)
        FileKind.Presentation -> Color(0xFFE0662E)
        FileKind.Ebook -> Color(0xFF9C6B3D)
        FileKind.Font -> Color(0xFF7A6CD9)
        FileKind.Database -> Color(0xFF3A8FB7)
        FileKind.Executable, FileKind.Library, FileKind.Dex -> Color(0xFF6E6E8E)
        FileKind.Script -> Color(0xFF3F9A5B)
        FileKind.Certificate -> Color(0xFFC79A2A)
        FileKind.DiskImage -> Color(0xFF8A7A6A)
        FileKind.Subtitle -> Color(0xFF7C8B3B)
        FileKind.Torrent -> Color(0xFF52A447)
        FileKind.Contact, FileKind.Calendar -> Color(0xFF3C83C9)
        FileKind.Patch -> Color(0xFFB9772E)
        FileKind.Unknown -> Color(0xFF7A808C)
    }

    /** Цвета, предлагаемые в редакторе иконок. */
    val palette: List<Color> = listOf(
        Color(0xFF4F7CF0), Color(0xFF3DBE6B), Color(0xFFE2A93B), Color(0xFFE5538C), Color(0xFFE5533D),
        Color(0xFFA55AE8), Color(0xFF6B7A90), Color(0xFF2E9AA8), Color(0xFF8F5CF6), Color(0xFFE07A2E),
        Color(0xFF3B7BD1), Color(0xFFD9B514), Color(0xFFD93A3A), Color(0xFF1F9D57), Color(0xFF9C6B3D),
        Color(0xFF7A6CD9), Color(0xFF3A8FB7), Color(0xFF52A447), Color(0xFF8A7A6A), Color(0xFF263238),
    )
}
