package client.wexmanager.core.fs

enum class FileKind(val label: String) {
    Folder("Папка"),
    Apk("Приложение Android"),
    Archive("Архив"),
    Image("Изображение"),
    Vector("Векторное изображение"),
    Video("Видео"),
    Audio("Аудио"),
    Text("Текст"),
    Code("Исходный код"),
    Markup("Разметка"),
    Data("Данные"),
    Config("Конфигурация"),
    Pdf("PDF-документ"),
    Document("Документ"),
    Spreadsheet("Таблица"),
    Presentation("Презентация"),
    Ebook("Электронная книга"),
    Font("Шрифт"),
    Database("База данных"),
    Executable("Исполняемый файл"),
    Script("Скрипт"),
    Dex("Байт-код Dalvik"),
    Library("Библиотека"),
    Certificate("Сертификат / ключ"),
    DiskImage("Образ диска"),
    Subtitle("Субтитры"),
    Torrent("Торрент"),
    Contact("Контакт"),
    Calendar("Календарь"),
    Web("Веб-страница"),
    Log("Журнал"),
    Patch("Патч"),
    Unknown("Файл");

    val isTextLike: Boolean
        get() = this == Text || this == Code || this == Markup || this == Data || this == Config ||
            this == Script || this == Web || this == Log || this == Patch || this == Subtitle ||
            this == Contact || this == Calendar || this == Vector

    companion object {
        private val map: Map<String, FileKind> = buildMap {
            fun put(kind: FileKind, vararg exts: String) = exts.forEach { put(it, kind) }
            put(Apk, "apk", "apks", "xapk", "apkm", "aab")
            put(Archive, "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "tbz2", "xz", "txz", "lz", "lzma", "zst", "cab", "jar", "war", "obb", "z", "lz4", "br", "ar", "cpio", "arj")
            put(Image, "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "ico", "tif", "tiff", "jxl", "raw", "dng", "cr2", "nef", "psd")
            put(Vector, "svg", "svgz")
            put(Video, "mp4", "mkv", "webm", "avi", "mov", "3gp", "3g2", "flv", "wmv", "m4v", "ts", "mts", "m2ts", "mpg", "mpeg", "ogv", "vob")
            put(Audio, "mp3", "wav", "ogg", "oga", "opus", "flac", "aac", "m4a", "wma", "amr", "mid", "midi", "ape", "alac", "aiff", "aif", "mka", "weba")
            put(Text, "txt", "text", "md", "markdown", "rst", "rtf", "nfo", "readme", "me", "asc", "adoc", "org", "tex")
            put(Code, "kt", "kts", "java", "c", "h", "cpp", "cc", "cxx", "hpp", "hh", "cs", "go", "rs", "swift", "dart", "scala", "groovy", "gradle", "lua", "pl", "pm", "rb", "r", "m", "mm", "php", "js", "mjs", "cjs", "jsx", "ts", "tsx", "py", "pyw", "vue", "svelte", "sql", "smali", "asm", "s", "vb", "fs", "ex", "exs", "erl", "hs", "clj", "elm", "zig", "nim", "jl", "v", "sv", "vhd", "glsl", "hlsl", "frag", "vert", "proto", "aidl", "cmake", "mk", "makefile", "dockerfile")
            put(Markup, "xml", "xhtml", "xsl", "xslt", "plist", "fxml", "xaml", "svg+xml")
            put(Web, "html", "htm", "css", "scss", "sass", "less", "mhtml", "jsp", "asp", "aspx")
            put(Data, "json", "json5", "jsonc", "yaml", "yml", "csv", "tsv", "geojson", "ndjson", "graphql", "gql", "toml")
            put(Config, "ini", "cfg", "conf", "config", "properties", "prop", "env", "gitignore", "gitattributes", "editorconfig", "pro", "rc", "lock", "reg", "desktop", "service", "manifest", "mf")
            put(Script, "sh", "bash", "zsh", "fish", "bat", "cmd", "ps1", "awk", "sed", "csh", "ksh")
            put(Log, "log", "out", "err", "trace", "logcat")
            put(Patch, "patch", "diff")
            put(Pdf, "pdf")
            put(Document, "doc", "docx", "odt", "pages", "wpd", "wps", "dot", "dotx")
            put(Spreadsheet, "xls", "xlsx", "ods", "numbers", "xlsm")
            put(Presentation, "ppt", "pptx", "odp", "key", "pps", "ppsx")
            put(Ebook, "epub", "mobi", "azw", "azw3", "fb2", "djvu", "cbz", "cbr", "chm")
            put(Font, "ttf", "otf", "woff", "woff2", "ttc", "fnt", "pfb")
            put(Database, "db", "sqlite", "sqlite3", "db3", "realm", "mdb", "accdb", "sql3", "frm", "ibd", "dbf")
            put(Executable, "exe", "msi", "elf", "bin", "run", "deb", "rpm", "dmg", "pkg", "appimage", "com", "o")
            put(Dex, "dex", "odex", "vdex", "oat", "art", "class")
            put(Library, "so", "dll", "dylib", "a", "lib", "aar", "ko")
            put(Certificate, "pem", "crt", "cer", "der", "key", "p12", "pfx", "jks", "keystore", "bks", "pub", "gpg", "sig", "rsa", "dsa", "pk8", "x509", "csr", "sf")
            put(DiskImage, "iso", "img", "vhd", "vhdx", "vmdk", "qcow2", "vdi", "sparse")
            put(Subtitle, "srt", "ass", "ssa", "vtt", "sub", "sbv", "lrc")
            put(Torrent, "torrent")
            put(Contact, "vcf", "vcard")
            put(Calendar, "ics", "ical")
        }

        private val nameMap: Map<String, FileKind> = mapOf(
            "makefile" to Code, "dockerfile" to Code, "cmakelists.txt" to Code,
            "readme" to Text, "license" to Text, "licence" to Text, "notice" to Text, "authors" to Text,
            "changelog" to Text, "copying" to Text, "gradlew" to Script, "build.prop" to Config,
            "androidmanifest.xml" to Markup, ".bashrc" to Script, ".profile" to Script, ".zshrc" to Script,
            "hosts" to Config, "fstab" to Config, "crontab" to Config,
        )

        fun of(name: String, isDirectory: Boolean): FileKind {
            if (isDirectory) return Folder
            val lower = name.lowercase()
            nameMap[lower]?.let { return it }
            val ext = lower.substringAfterLast('.', "")
            if (ext.isEmpty()) return Unknown
            return map[ext] ?: Unknown
        }

        fun forExtension(ext: String): FileKind = map[ext.lowercase()] ?: Unknown

        val knownExtensions: Map<String, FileKind> get() = map
    }
}
