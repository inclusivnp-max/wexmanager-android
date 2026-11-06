package client.wexmanager.feature.editor.lang

import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Символы Go-проекта для подсказок: экспортированные объявления всех .go-файлов рядом с открытым
 * файлом, имена пакетов и зависимости из go.mod. Скан ограничен по числу файлов и времени, идёт
 * в фоне и кэшируется по корню проекта, чтобы не тормозить ввод.
 */
object GoWorkspaceIndex {
    private const val MAX_FILES = 600
    private const val MAX_FILE_BYTES = 512 * 1024
    private const val TTL_NANOS = 20_000_000_000L
    private val SKIP_DIRS = setOf(".git", ".idea", "testdata", "vendor", "node_modules", ".gradle", "build")

    class Snapshot(
        val root: String,
        /** Имя пакета -> символы (только экспортированные и с краткой подсказкой). */
        val packages: Map<String, List<VocabWord>>,
        /** Пути локальных пакетов проекта (для «import „…"»): importPath относительный. */
        val importPaths: List<String>,
        val at: Long,
    ) {
        /** Готовый список переиспользуется подсказками вместо flatten/distinct на каждый ввод. */
        val allSymbols: List<VocabWord> by lazy(LazyThreadSafetyMode.PUBLICATION) {
            packages.values.asSequence().flatten().distinctBy { it.text + it.kind }.toList()
        }
    }

    private val cached = AtomicReference<Snapshot?>(null)
    private val building = java.util.concurrent.atomic.AtomicBoolean(false)
    private val generation = java.util.concurrent.atomic.AtomicLong(0L)

    /**
     * Сведения о ближайшем go.mod кэшируются: путь ищется по словарю каталогов, содержимое
     * перечитывается только при изменении файла. Иначе подсказки читали бы go.mod на каждую букву.
     */
    private class ModuleInfo(val root: File, val deps: List<String>, val stamp: Long)
    private val moduleCache = java.util.concurrent.ConcurrentHashMap<String, ModuleInfo>()

    /** Сбрасывает индекс после go mod tidy / создания файла, не останавливая UI-поток. */
    fun invalidate(root: File? = null) {
        val rootPath = root?.let { runCatching { it.canonicalPath }.getOrNull() }
        if (rootPath == null) {
            generation.incrementAndGet()
            cached.set(null)
            moduleCache.clear()
            return
        }
        val current = cached.get()
        if (current?.root == rootPath) {
            generation.incrementAndGet()
            cached.set(null)
        }
        moduleCache.entries.removeIf { it.value.root.path == rootPath }
    }

    private val exportFunc = Regex("(?m)^func\\s+(?:\\([^)]*\\)\\s*)?([A-Z][\\w]*)\\s*\\(")
    private val exportType = Regex("(?m)^type\\s+([A-Z][\\w]*)\\b")
    private val exportVar = Regex("(?m)^(?:var|const)\\s+([A-Z][\\w]*)\\b")
    private val exportVarGroup = Regex("(?m)^\\t([A-Z][\\w]*)\\s")
    private val packageName = Regex("(?m)^package\\s+([\\w]+)")
    private val requireLine = Regex("(?m)^\\s*(?:require\\s+)?([\\w.\\-/]+\\.[\\w.\\-/]+)\\s+v[\\w.\\-+]+")

    /** Модуль и его зависимости; null — если go.mod не найден. */
    fun moduleInfo(start: File?): Pair<File, List<String>>? {
        var dir: File? = start?.let { runCatching { it.canonicalFile }.getOrNull() } ?: return null
        val origin = dir?.path ?: return null
        moduleCache[origin]?.let { cachedInfo ->
            val modFile = File(cachedInfo.root, "go.mod")
            if (modFile.isFile && modFile.lastModified() == cachedInfo.stamp) {
                return cachedInfo.root to cachedInfo.deps
            }
            moduleCache.remove(origin, cachedInfo)
        }
        var found: File? = null
        while (true) {
            val current = dir ?: break
            val candidate = File(current, "go.mod")
            if (candidate.isFile) { found = current; break }
            dir = current.parentFile
        }
        val moduleRoot = found ?: return null
        val file = File(moduleRoot, "go.mod")
        val stamp = file.lastModified()
        val deps = runCatching {
            requireLine.findAll(file.readText().take(256 * 1024)).map { it.groupValues[1] }.distinct().take(200).toList()
        }.getOrDefault(emptyList())
        val info = ModuleInfo(moduleRoot, deps, stamp)
        moduleCache[origin] = info
        return moduleRoot to deps
    }

    /** Кэш по корню; при устаревании обновляется в фоне, старые данные продолжают использоваться. */
    fun snapshot(start: File?): Snapshot? {
        val module = moduleInfo(start) ?: return null
        val root = module.first
        val now = System.nanoTime()
        val current = cached.get()
        if (current != null && current.root == root.path && now - current.at < TTL_NANOS) return current
        val ticket = generation.get()
        if (building.compareAndSet(false, true)) {
            Thread({
                try {
                    val built = scan(root)
                    if (generation.get() == ticket) cached.set(built)
                } catch (_: Throwable) {
                } finally {
                    building.set(false)
                }
            }, "wex-go-workspace-index").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
        }
        return current?.takeIf { it.root == root.path }
    }

    /** Доступно тестам: синхронный скан без кэша и фоновых потоков. */
    internal fun scanForTest(root: File): Snapshot = scan(root)

    private fun scan(root: File): Snapshot {
        val packages = HashMap<String, MutableList<VocabWord>>()
        val imports = ArrayList<String>()
        val modulePath = runCatching {
            Regex("(?m)^module\\s+(\\S+)").find(File(root, "go.mod").readText())?.groupValues?.get(1).orEmpty()
        }.getOrDefault("")
        var files = 0
        val stack = ArrayDeque<File>().apply { add(root) }
        while (stack.isNotEmpty() && files < MAX_FILES) {
            val dir = stack.removeFirst()
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                if (files >= MAX_FILES) break
                if (child.isDirectory) {
                    if (child.name !in SKIP_DIRS && !child.name.startsWith('.')) stack.add(child)
                    continue
                }
                if (!child.name.endsWith(".go") || child.length() > MAX_FILE_BYTES) continue
                files++
                val text = runCatching { child.readText() }.getOrNull() ?: continue
                val pkg = packageName.find(text)?.groupValues?.get(1) ?: continue
                val list = packages.getOrPut(pkg) { ArrayList() }
                exportFunc.findAll(text).forEach { list.add(VocabWord(it.groupValues[1], ItemKind.FUNCTION, "func · $pkg")) }
                exportType.findAll(text).forEach { list.add(VocabWord(it.groupValues[1], ItemKind.TYPE, "type · $pkg")) }
                exportVar.findAll(text).forEach { list.add(VocabWord(it.groupValues[1], ItemKind.VARIABLE, "var · $pkg")) }
                exportVarGroup.findAll(text).forEach { list.add(VocabWord(it.groupValues[1], ItemKind.VARIABLE, "const · $pkg")) }
                if (child.name != "main.go") {
                    val parent = child.parentFile ?: root
                    val rel = runCatching { parent.relativeTo(root).invariantSeparatorsPath }.getOrDefault("")
                    val importPath = when {
                        rel.isBlank() -> modulePath
                        modulePath.isNotBlank() -> "$modulePath/$rel"
                        else -> rel
                    }
                    if (importPath.isNotBlank() && imports.size < 200 && importPath !in imports) imports.add(importPath)
                }
            }
        }
        val deduped = packages.mapValues { (_, items) ->
            items.distinctBy { it.text + it.kind }.take(600)
        }
        return Snapshot(root.path, deduped, imports.sorted(), System.nanoTime())
    }
}
