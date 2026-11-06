package client.wexmanager.feature.editor.golang

import java.io.File
import java.nio.file.Files

data class GoTreeNode(val file: File, val depth: Int, val directory: Boolean, val expanded: Boolean)
data class GoTreeSnapshot(val nodes: List<GoTreeNode>, val truncated: Boolean)

/** File operations stay inside the canonical project root, including symlink targets. */
internal object GoWorkspaceFiles {
    fun contained(root: File, file: File): Boolean = file.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())

    fun validName(name: String) {
        require(name.isNotBlank() && name == name.trim() && name !in listOf(".", "..") && name.none { it == '/' || it == '\\' || it.code < 32 }) {
            "Введите имя без /, \\ и управляющих символов"
        }
    }

    fun validModule(module: String) {
        require(module.matches(Regex("[A-Za-z0-9][A-Za-z0-9._/-]*")) && module.split('/').none { it.isBlank() || it == "." || it == ".." }) {
            "Имя модуля: например example.com/my-app"
        }
    }

    fun createProject(base: File, name: String, module: String): File {
        validName(name)
        validModule(module)
        require(base.mkdirs() || base.isDirectory) { "Откройте доступ к общему хранилищу" }
        val root = File(base, name)
        require(contained(base, root) && root.mkdir()) { "Папка уже существует или недоступна" }
        try {
            File(root, "go.mod").writeText("module $module\n\ngo 1.22\n")
            File(root, "main.go").writeText("package main\n\nimport \"fmt\"\n\nfunc main() {\n\tfmt.Println(\"Hello, Go!\")\n}\n")
            File(root, ".gitignore").writeText("/bin/\n*.test\n")
        } catch (e: Exception) {
            root.listFiles()?.forEach { it.delete() }
            root.delete()
            throw e
        }
        return root.canonicalFile
    }

    fun createEntry(root: File, parent: File, name: String, folder: Boolean): File {
        validName(name)
        require(contained(root, parent) && parent.isDirectory) { "Выберите папку внутри проекта" }
        val file = File(parent, name)
        require(contained(root, file) && !file.exists()) { "Файл уже существует или находится вне проекта" }
        if (folder) require(file.mkdir()) { "Ошибка создания папки" }
        else {
            require(file.createNewFile()) { "Ошибка создания файла" }
            if (file.extension == "go") {
                val pkg = parent.listFiles()?.asSequence()?.filter { it != file && it.extension == "go" && it.length() < 1_000_000 }
                    ?.sortedBy { it.name.endsWith("_test.go") }
                    ?.mapNotNull { source -> source.bufferedReader().use { reader ->
                        Regex("(?m)^\\s*package\\s+([A-Za-z_][A-Za-z0-9_]*)").find(reader.readText().take(16_000))?.groupValues?.get(1)
                    } }?.firstOrNull() ?: "main"
                file.writeText("package $pkg\n\n")
            }
        }
        return file.canonicalFile
    }

    fun tree(root: File, expanded: Set<String>, filter: String = "", limit: Int = 4000): GoTreeSnapshot {
        require(root.isDirectory && root.canRead()) { "Папка проекта недоступна: $root" }
        val nodes = ArrayList<GoTreeNode>()
        var truncated = false
        fun visit(parent: File, depth: Int) {
            if (depth > 48 || nodes.size >= limit) { truncated = true; return }
            val children = parent.listFiles() ?: return
            for (child in children.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }, { it.name }))) {
                if (child.name == ".git") continue
                if (nodes.size >= limit) { truncated = true; break }
                val directory = child.isDirectory && !Files.isSymbolicLink(child.toPath()) && contained(root, child)
                if (!directory && filter.isNotBlank() && !child.name.contains(filter, ignoreCase = true)) continue
                val open = directory && child.absolutePath in expanded
                nodes += GoTreeNode(child, depth, directory, open)
                if (open) visit(child, depth + 1)
            }
        }
        visit(root.canonicalFile, 0)
        return GoTreeSnapshot(nodes, truncated)
    }


    fun parents(root: File, file: File): Set<String> {
        require(contained(root, file)) { "Файл вне проекта" }
        val result = LinkedHashSet<String>()
        var parent = file.canonicalFile.parentFile
        val canonicalRoot = root.canonicalFile
        while (parent != null && parent != canonicalRoot && contained(canonicalRoot, parent)) {
            result += parent.path
            parent = parent.parentFile
        }
        return result
    }

    fun search(root: File, query: String, limit: Int = 200, visitLimit: Int = 20_000): GoTreeSnapshot {
        require(root.isDirectory && root.canRead()) { "Папка проекта недоступна" }
        val needle = query.trim().lowercase(java.util.Locale.ROOT)
        if (needle.isEmpty()) return GoTreeSnapshot(emptyList(), false)
        val matches = ArrayList<File>()
        var visited = 0
        var truncated = false
        fun visit(dir: File, depth: Int) {
            if (depth > 48 || visited >= visitLimit) { truncated = true; return }
            for (file in dir.listFiles().orEmpty()) {
                if (++visited > visitLimit) { truncated = true; break }
                if (file.name == ".git" || Files.isSymbolicLink(file.toPath()) || !contained(root, file)) continue
                if (file.isDirectory) visit(file, depth + 1)
                else if (file.name.lowercase(java.util.Locale.ROOT).contains(needle) || file.relativeTo(root).invariantSeparatorsPath.lowercase(java.util.Locale.ROOT).contains(needle)) matches += file
            }
        }
        visit(root, 0)
        fun rank(file: File): Int {
            val name = file.name.lowercase(java.util.Locale.ROOT)
            return when { name == needle -> 0; name.startsWith(needle) -> 1; name.contains(needle) -> 2; else -> 3 }
        }
        val ordered = matches.sortedWith(compareBy(::rank, { it.relativeTo(root).invariantSeparatorsPath.length }, { it.path }))
        return GoTreeSnapshot(ordered.take(limit).map { GoTreeNode(it, 0, false, false) }, truncated || matches.size > limit)
    }

    fun textFile(root: File, file: File): File {
        require(contained(root, file) && file.isFile && file.canRead()) { "Файл недоступен или находится вне проекта" }
        val bytes = file.inputStream().use { stream -> ByteArray(4096).let { buf -> buf.take(stream.read(buf).coerceAtLeast(0)) } }
        require(bytes.none { it == 0.toByte() }) { "Бинарный файл: откройте его в файловом менеджере" }
        return file.canonicalFile
    }
}
