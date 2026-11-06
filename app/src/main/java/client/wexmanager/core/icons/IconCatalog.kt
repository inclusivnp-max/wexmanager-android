package client.wexmanager.core.icons

import androidx.compose.runtime.Immutable

/**
 * Каталог иконпаков для загрузки в одно касание. Иконки берутся по отдельности (SVG) с CDN jsDelivr
 * из открытых наборов под лицензией MIT; версии закреплены, сопоставление «расширение → значок»
 * сверено со списком файлов этих версий.
 */
@Immutable
data class CatalogPack(
    val id: String,
    val name: String,
    val author: String,
    val license: String,
    val description: String,
    val homepage: String,
    val baseUrl: String,
    val icons: Map<String, String>,
)

object IconCatalog {
    private val MATERIAL_MAP = mapOf("*" to "document", "3gp" to "video", "7z" to "zip", "apk" to "android", "apks" to "android", "avi" to "video", "bash" to "console", "bat" to "console", "bin" to "exe", "bmp" to "image", "bz2" to "zip", "c" to "c", "cc" to "cpp", "cer" to "certificate", "cfg" to "settings", "cjs" to "javascript", "cmd" to "console", "conf" to "settings", "cpp" to "cpp", "crt" to "certificate", "cs" to "csharp", "css" to "css", "csv" to "table", "dart" to "dart", "db" to "database", "dex" to "android", "diff" to "diff", "dll" to "dll", "doc" to "word", "docx" to "word", "eml" to "email", "epub" to "epub", "exe" to "exe", "flac" to "audio", "folder" to "folder-base", "gif" to "image", "go" to "go", "gradle" to "gradle", "gz" to "zip", "h" to "h", "heic" to "image", "hpp" to "hpp", "htm" to "html", "html" to "html", "img" to "disc", "ini" to "settings", "iso" to "disc", "jar" to "java", "java" to "java", "jks" to "key", "jpeg" to "image", "jpg" to "image", "js" to "javascript", "json" to "json", "jsx" to "react", "keystore" to "key", "kt" to "kotlin", "kts" to "kotlin", "lock" to "lock", "log" to "log", "lua" to "lua", "m4a" to "audio", "markdown" to "markdown", "md" to "markdown", "mjs" to "javascript", "mkv" to "video", "mov" to "video", "mp3" to "audio", "mp4" to "video", "ogg" to "audio", "opus" to "audio", "otf" to "font", "p12" to "key", "patch" to "diff", "pdf" to "pdf", "pem" to "certificate", "php" to "php", "pk8" to "key", "png" to "image", "ppt" to "powerpoint", "pptx" to "powerpoint", "properties" to "settings", "ps1" to "powershell", "py" to "python", "rar" to "zip", "rb" to "ruby", "rs" to "rust", "sass" to "sass", "scss" to "sass", "sh" to "console", "smali" to "assembly", "so" to "dll", "sql" to "database", "sqlite" to "database", "svg" to "svg", "swift" to "swift", "tar" to "zip", "tex" to "tex", "toml" to "toml", "ts" to "typescript", "tsx" to "react_ts", "ttf" to "font", "txt" to "document", "vue" to "vue", "wav" to "audio", "webm" to "video", "webp" to "image", "woff" to "font", "woff2" to "font", "xls" to "table", "xlsx" to "table", "xml" to "xml", "xz" to "zip", "yaml" to "yaml", "yml" to "yaml", "zip" to "zip", "zsh" to "console")
    private val CATPPUCCIN_MAP = mapOf("*" to "_file", "3gp" to "video", "7z" to "zip", "apk" to "android", "apks" to "android", "avi" to "video", "bash" to "bash", "bin" to "binary", "bmp" to "image", "bz2" to "zip", "c" to "c", "cc" to "cpp", "cer" to "certificate", "cfg" to "config", "cjs" to "javascript", "conf" to "config", "cpp" to "cpp", "crt" to "certificate", "cs" to "csharp", "css" to "css", "csv" to "csv", "dart" to "dart", "db" to "database", "dex" to "android", "diff" to "diff", "dll" to "exe", "exe" to "exe", "flac" to "audio", "folder" to "_folder", "gif" to "image", "go" to "go", "gradle" to "gradle", "gz" to "zip", "h" to "c", "heic" to "image", "hpp" to "cpp", "htm" to "html", "html" to "html", "ini" to "config", "jar" to "java", "java" to "java", "jks" to "key", "jpeg" to "image", "jpg" to "image", "js" to "javascript", "json" to "json", "keystore" to "key", "kt" to "kotlin", "kts" to "kotlin", "lock" to "lock", "log" to "log", "lua" to "lua", "m4a" to "audio", "markdown" to "markdown", "md" to "markdown", "mjs" to "javascript", "mkv" to "video", "mov" to "video", "mp3" to "audio", "mp4" to "video", "ogg" to "audio", "opus" to "audio", "otf" to "font", "p12" to "key", "patch" to "diff", "pdf" to "pdf", "pem" to "certificate", "php" to "php", "pk8" to "key", "png" to "image", "properties" to "properties", "ps1" to "powershell", "py" to "python", "rar" to "zip", "rb" to "ruby", "rs" to "rust", "sass" to "sass", "scss" to "sass", "sh" to "bash", "smali" to "assembly", "so" to "binary", "sql" to "database", "sqlite" to "database", "svg" to "svg", "swift" to "swift", "tar" to "zip", "toml" to "toml", "ts" to "typescript", "ttf" to "font", "txt" to "text", "vue" to "vue", "wav" to "audio", "webm" to "video", "webp" to "image", "woff" to "font", "woff2" to "font", "xls" to "csv", "xlsx" to "csv", "xml" to "xml", "xz" to "zip", "yaml" to "yaml", "yml" to "yaml", "zip" to "zip", "zsh" to "bash")

    val packs: List<CatalogPack> = listOf(
        CatalogPack(
            id = "catalog-material-icon-theme",
            name = "Material Icon Theme",
            author = "Philipp Kief и участники",
            license = "MIT",
            description = "Яркие цветные значки из VS Code: языки, документы, медиа, архивы",
            homepage = "https://github.com/material-extensions/vscode-material-icon-theme",
            baseUrl = "https://cdn.jsdelivr.net/gh/material-extensions/vscode-material-icon-theme@5.39.0/icons/",
            icons = MATERIAL_MAP,
        ),
        CatalogPack(
            id = "catalog-catppuccin-mocha",
            name = "Catppuccin Mocha",
            author = "Catppuccin",
            license = "MIT",
            description = "Пастельные значки для тёмной темы",
            homepage = "https://github.com/catppuccin/vscode-icons",
            baseUrl = "https://cdn.jsdelivr.net/gh/catppuccin/vscode-icons@1.26.0/icons/mocha/",
            icons = CATPPUCCIN_MAP,
        ),
        CatalogPack(
            id = "catalog-catppuccin-latte",
            name = "Catppuccin Latte",
            author = "Catppuccin",
            license = "MIT",
            description = "Пастельные значки для светлой темы",
            homepage = "https://github.com/catppuccin/vscode-icons",
            baseUrl = "https://cdn.jsdelivr.net/gh/catppuccin/vscode-icons@1.26.0/icons/latte/",
            icons = CATPPUCCIN_MAP,
        ),
    )
}
