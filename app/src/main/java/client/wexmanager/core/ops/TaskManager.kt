package client.wexmanager.core.ops

import android.content.Context
import android.content.Intent
import android.system.Os
import android.system.OsConstants
import androidx.core.content.ContextCompat
import client.wexmanager.core.archive.ArchiveEngine
import client.wexmanager.core.archive.ArchiveFormat
import client.wexmanager.core.fs.FileItem
import client.wexmanager.core.fs.LocalFileSystem
import client.wexmanager.core.fs.SafAccess
import client.wexmanager.core.util.Format
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class TaskManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val trash: TrashManager,
) {
    private val _tasks = MutableStateFlow<List<TaskState>>(emptyList())
    val tasks: StateFlow<List<TaskState>> = _tasks.asStateFlow()

    private val _events = MutableSharedFlow<TaskEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<TaskEvent> = _events.asSharedFlow()

    private val jobs = ConcurrentHashMap<Long, Job>()
    private val taskErrors = CoroutineExceptionHandler { _, e -> Log.w("TaskManager", "task failed", e) }
    private val ids = AtomicLong(1)

    val hasActive: Boolean get() = _tasks.value.any { it.isActive }

    fun copy(sources: List<FileItem>, destDir: String, move: Boolean): Long {
        val kind = if (move) TaskKind.Move else TaskKind.Copy
        val title = "${kind.verb}: ${describe(sources)}"
        return launch(kind, title, destDir) {
            copyItems(sources, destDir, move)
            touched += destDir
            sources.forEach { s -> File(s.path).parent?.let { touched += it } }
        }
    }

    fun delete(items: List<FileItem>, toTrash: Boolean): Long {
        val kind = if (toTrash) TaskKind.Trash else TaskKind.Delete
        return launch(kind, "${kind.verb}: ${describe(items)}", null) {
            items.forEach { s -> File(s.path).parent?.let { touched += it } }
            if (toTrash) trashItems(items) else deleteItems(items)
        }
    }

    fun restoreFromTrash(entries: List<TrashManager.TrashEntry>): Long =
        launch(TaskKind.Restore, "Восстановление: ${Format.items(entries.size)}", null) {
            totalItems = entries.size
            status = TaskStatus.Running
            for (e in entries) {
                currentCoroutineContext().ensureActive()
                currentName = e.name
                runCatching { trash.restore(e) }
                    .onSuccess { touched += File(it).parent ?: "" }
                    .onFailure { failures += "${e.name}: ${it.message}" }
                processedItems++
                publish()
            }
        }

    fun emptyTrash(entries: List<TrashManager.TrashEntry>): Long =
        launch(TaskKind.Delete, "Очистка корзины", null) {
            totalItems = entries.size
            status = TaskStatus.Running
            for (e in entries) {
                currentCoroutineContext().ensureActive()
                currentName = e.name
                try {
                    trash.purge(e)
                } catch (err: IOException) {
                    failures += "${e.name}: ${err.message}"
                }
                processedItems++
                publish()
            }
        }

    fun compress(sources: List<FileItem>, target: File, format: ArchiveFormat, level: Int, password: String? = null): Long =
        launch(TaskKind.Compress, "Сжатие: ${target.name}", target.parent) {
            status = TaskStatus.Preparing
            val (files, bytes) = scan(sources.map { File(it.path) })
            totalItems = files
            totalBytes = bytes
            status = TaskStatus.Running
            publish(force = true)
            ArchiveEngine.compress(sources.map { File(it.path) }, target, format, level, password) { name, delta ->
                currentName = name
                if (delta < 0) processedItems++ else processedBytes += delta
                publish()
            }
            resultPath = target.path
            touched += target.parent ?: ""
        }

    /** Повторы задач, остановившихся на пароле архива: id → запуск с паролем. */
    private val passwordRetries = ConcurrentHashMap<Long, (String) -> Long>()

    fun retryWithPassword(id: Long, password: String): Long? {
        val retry = passwordRetries.remove(id) ?: return null
        _tasks.update { list -> list.filterNot { it.id == id } }
        return retry(password)
    }

    fun extract(archive: File, destDir: File, entries: List<String>? = null, basePath: String = "", password: String? = null): Long =
        launch(TaskKind.Extract, "Распаковка: ${archive.name}", destDir.path, retry = { pw -> extract(archive, destDir, entries, basePath, pw) }) {
            status = TaskStatus.Running
            totalBytes = archive.length()
            ArchiveEngine.extract(
                archive, destDir, entries, password, basePath,
                onTotal = { items, bytes -> totalItems = items; if (bytes > 0) totalBytes = bytes },
                onConflict = { src, dst -> askConflict(src, dst) },
            ) { name, delta ->
                currentName = name
                if (delta < 0) processedItems++ else processedBytes += delta
                publish()
            }
            resultPath = destDir.path
            touched += destDir.path
            touched += destDir.parent ?: ""
        }

    /** Проверка целостности архива: повреждённые записи попадают в список ошибок задачи. */
    fun verifyArchive(archive: File, password: String? = null): Long =
        launch(TaskKind.Verify, "Проверка: ${archive.name}", archive.parent, retry = { pw -> verifyArchive(archive, pw) }) {
            status = TaskStatus.Running
            totalBytes = archive.length()
            val bad = ArchiveEngine.verify(
                archive, password,
                onTotal = { items, bytes -> if (items >= 0) totalItems = items; if (bytes > 0) totalBytes = bytes },
            ) { name, delta ->
                currentName = name
                if (delta < 0) processedItems++ else processedBytes += delta
                publish()
            }
            failures += bad
        }

    /** Добавить файлы/папки в zip-архив в папку [innerDir]. */
    fun addToArchive(sources: List<FileItem>, archive: File, innerDir: String): Long =
        launch(TaskKind.Compress, "Добавление в ${archive.name}: ${describe(sources)}", archive.path) {
            status = TaskStatus.Preparing
            publish(force = true)
            val map = LinkedHashMap<String, File>()
            for (src in sources) {
                val root = File(src.path)
                val base = root.parent ?: ""
                val stack = ArrayDeque<File>().apply { add(root) }
                while (stack.isNotEmpty()) {
                    currentCoroutineContext().ensureActive()
                    val f = stack.removeLast()
                    if (f.isDirectory) {
                        if (!isSymlink(f)) f.listFiles()?.let { stack.addAll(it) }
                    } else {
                        val rel = f.path.removePrefix(base).trimStart('/')
                        map[if (innerDir.isEmpty()) rel else "$innerDir/$rel"] = f
                    }
                }
            }
            // Записи, которые уже есть в архиве, — через обычный диалог конфликта.
            val index = runCatching { ArchiveEngine.index(archive) }.getOrNull()
            if (index != null) {
                for (key in map.keys.toList()) {
                    val parent = key.substringBeforeLast('/', "")
                    val existing = index.list(parent)?.firstOrNull { it.path == key && !it.isDirectory } ?: continue
                    val src = map[key] ?: continue
                    val d = askConflict(LocalFileSystem.stat(src.path), existing)
                    when (d.choice) {
                        ConflictChoice.Skip -> map.remove(key)
                        ConflictChoice.KeepBoth -> {
                            map.remove(key)
                            val dot = key.lastIndexOf('.').takeIf { it > key.lastIndexOf('/') } ?: key.length
                            var n = 1
                            var alt: String
                            do { alt = key.substring(0, dot) + " ($n)" + key.substring(dot); n++ } while (index.list(parent)?.any { it.path == alt } == true)
                            map[alt] = src
                        }
                        else -> Unit
                    }
                }
            }
            if (map.isEmpty()) return@launch
            totalItems = map.size
            totalBytes = map.values.sumOf { it.length() }
            status = TaskStatus.Running
            currentName = archive.name
            publish(force = true)
            ArchiveEngine.modifyZip(archive, map)
            processedItems = totalItems
            processedBytes = totalBytes
            touched += archive.parent ?: ""
        }

    /** Удалить записи (файлы или папки) из zip-архива. */
    fun deleteFromArchive(archive: File, entries: List<String>): Long =
        launch(TaskKind.Delete, "Удаление из ${archive.name}: ${Format.items(entries.size)}", null) {
            status = TaskStatus.Running
            totalItems = entries.size
            currentName = archive.name
            publish(force = true)
            ArchiveEngine.modifyZip(archive, emptyMap(), entries.toSet())
            processedItems = entries.size
            touched += archive.parent ?: ""
        }

    /** Подпись APK ключом приложения; результат — «имя_signed.apk» рядом с исходником. */
    fun signApk(apk: File, keyAlias: String? = null): Long {
        val base = apk.name.removeSuffix(".apk")
        return launch(TaskKind.Sign, "Подпись: ${apk.name}", apk.parent) {
            status = TaskStatus.Preparing
            currentName = apk.name
            publish(force = true)
            // Имя резервируем пустым файлом сразу: параллельная подпись того же APK получит другое.
            val out = synchronized(this@TaskManager) {
                File(apk.parentFile, LocalFileSystem.uniqueName(apk.parent ?: "/", "${base}_signed.apk")).also { it.createNewFile() }
            }
            try {
                client.wexmanager.core.apk.ApkSigning.sign(apk, out, keyAlias)
            } catch (e: Throwable) {
                if (out.length() == 0L) out.delete() // не оставляем пустую заглушку
                throw e
            }
            resultPath = out.path
            touched += apk.parent ?: ""
        }
    }

    fun cancel(id: Long) {
        jobs[id]?.cancel()
    }

    fun dismiss(id: Long) {
        passwordRetries.remove(id)
        _tasks.update { list -> list.filterNot { it.id == id && !it.isActive } }
    }

    fun dismissFinished() {
        _tasks.update { list -> list.filter { it.isActive } }
    }

    private fun describe(items: List<FileItem>): String =
        if (items.size == 1) items[0].name else Format.items(items.size)

    private fun launch(
        kind: TaskKind,
        title: String,
        destination: String?,
        retry: ((String) -> Long)? = null,
        block: suspend TaskContext.() -> Unit,
    ): Long {
        val id = ids.getAndIncrement()
        _tasks.update { it + TaskState(id = id, kind = kind, title = title, destination = destination) }
        val ctx = TaskContext(id)
        // Ошибка задачи не должна ронять процесс: причина всё равно придёт в invokeOnCompletion → Failed.
        val job = scope.launch(Dispatchers.IO + taskErrors) { ctx.block() }
        jobs[id] = job
        job.invokeOnCompletion { cause ->
            jobs.remove(id)
            val status = when {
                cause is CancellationException -> TaskStatus.Cancelled
                cause != null -> TaskStatus.Failed
                ctx.failures.isNotEmpty() -> TaskStatus.Failed
                else -> TaskStatus.Done
            }
            val error = when {
                cause is CancellationException -> null
                cause != null -> cause.message ?: cause.javaClass.simpleName
                ctx.failures.isNotEmpty() -> "Ошибок: ${ctx.failures.size}"
                else -> null
            }
            ctx.conflictDeferred?.cancel()
            val pwError = cause as? client.wexmanager.core.archive.ArchivePasswordException
            if (pwError != null && retry != null) passwordRetries[id] = retry
            var finished: TaskState? = null
            _tasks.update { list ->
                list.map {
                    if (it.id != id) it else ctx.snapshot(it).copy(
                        status = status,
                        error = error,
                        failedItems = ctx.failures.take(50),
                        conflict = null,
                        finishedAt = System.currentTimeMillis(),
                        needsPassword = pwError != null && retry != null,
                        passwordWrong = pwError?.wrong == true,
                    ).also { s -> finished = s }
                }
            }
            finished?.let { _events.tryEmit(TaskEvent.Finished(it, ctx.touched.toSet())) }
        }
        startService()
        return id
    }

    private fun startService() {
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, FileOperationService::class.java))
        }
    }

    inner class TaskContext(val id: Long) {
        @Volatile var status: TaskStatus = TaskStatus.Preparing
        @Volatile var currentName: String = ""
        @Volatile var processedBytes: Long = 0
        @Volatile var totalBytes: Long = 0
        @Volatile var processedItems: Int = 0
        @Volatile var totalItems: Int = 0
        @Volatile var resultPath: String? = null
        val trashed: MutableList<TrashManager.TrashEntry> = java.util.Collections.synchronizedList(mutableListOf())
        val failures: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
        val touched: MutableSet<String> = ConcurrentHashMap.newKeySet()
        internal var conflictDeferred: CompletableDeferred<ConflictDecision>? = null
        private var cachedFile: ConflictDecision? = null
        private var cachedDir: ConflictDecision? = null
        private var lastPublish = 0L
        private var speedMark = System.currentTimeMillis()
        private var speedBytes = 0L
        private var speed = 0L

        fun snapshot(base: TaskState): TaskState = base.copy(
            status = status,
            currentName = currentName,
            processedBytes = processedBytes,
            totalBytes = totalBytes,
            processedItems = processedItems,
            totalItems = totalItems,
            bytesPerSecond = speed,
            resultPath = resultPath,
            trashed = synchronized(trashed) { trashed.toList() },
        )

        fun publish(force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (!force && now - lastPublish < 120) return
            lastPublish = now
            val dt = now - speedMark
            if (dt >= 700) {
                speed = (processedBytes - speedBytes) * 1000 / dt
                speedBytes = processedBytes
                speedMark = now
            }
            _tasks.update { list -> list.map { if (it.id == id) snapshot(it) else it } }
        }

        /**
         * Решение конфликта. «Применить ко всем» запоминается отдельно для файлов и для папок;
         * конфликты разных типов (файл против папки) никогда не берутся из кеша.
         */
        suspend fun askConflict(source: FileItem, target: FileItem): ConflictDecision {
            val dirs = source.isDirectory && target.isDirectory
            val sameType = source.isDirectory == target.isDirectory
            if (sameType) (if (dirs) cachedDir else cachedFile)?.let { return it }
            val deferred = CompletableDeferred<ConflictDecision>()
            conflictDeferred = deferred
            val previous = status
            status = TaskStatus.WaitingConflict
            _tasks.update { list -> list.map { if (it.id == id) snapshot(it).copy(conflict = ConflictRequest(source, target, deferred)) else it } }
            val decision = deferred.await()
            conflictDeferred = null
            status = previous
            _tasks.update { list -> list.map { if (it.id == id) snapshot(it).copy(conflict = null) else it } }
            if (decision.applyToAll && sameType) if (dirs) cachedDir = decision else cachedFile = decision
            return decision
        }

        suspend fun scan(roots: List<File>): Pair<Int, Long> {
            var files = 0
            var bytes = 0L
            val stack = ArrayDeque(roots)
            while (stack.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val f = stack.removeLast()
                if (SafAccess.needsSaf(context, f.path)) {
                    val item = SafAccess.stat(context, f.path)
                    if (item?.isDirectory == true) SafAccess.list(context, f.path).forEach { stack.add(File(it.path)) }
                    else {
                        files++
                        bytes += item?.size ?: 0L
                    }
                    continue
                }
                if (f.isDirectory && !isSymlink(f)) {
                    f.listFiles()?.let { stack.addAll(it) }
                } else {
                    files++
                    bytes += f.length()
                }
            }
            return files to bytes
        }

        /** Количество всех узлов (файлы + папки) — для прогресса удаления. */
        private suspend fun countNodes(roots: List<File>): Int {
            var n = 0
            val stack = ArrayDeque(roots)
            while (stack.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val f = stack.removeLast()
                n++
                if (SafAccess.needsSaf(context, f.path)) {
                    val item = SafAccess.stat(context, f.path)
                    if (item?.isDirectory == true) SafAccess.list(context, f.path).forEach { stack.add(File(it.path)) }
                } else if (f.isDirectory && !isSymlink(f)) f.listFiles()?.let { stack.addAll(it) }
            }
            return n
        }

        suspend fun copyItems(sources: List<FileItem>, destDir: String, move: Boolean) {
            status = TaskStatus.Preparing
            publish(force = true)
            val (files, bytes) = scan(sources.map { File(it.path) })
            totalItems = files
            totalBytes = bytes
            status = TaskStatus.Running
            publish(force = true)
            val dest = File(destDir)
            if (!dest.exists() && !makeDirectory(dest)) throw IOException("Не удалось создать папку ${dest.path}")
            for (src in sources) {
                currentCoroutineContext().ensureActive()
                val srcFile = File(src.path)
                var target = File(dest, src.name)
                if (src.isDirectory && (isInside(dest, srcFile))) {
                    failures += "${src.name}: нельзя ${if (move) "переместить" else "скопировать"} папку в саму себя"
                    continue
                }
                if (target.absolutePath == srcFile.absolutePath || sameFile(srcFile, target)) {
                    if (move) continue
                    target = File(dest, LocalFileSystem.uniqueName(destDir, src.name))
                }
                if (move && !target.exists() && srcFile.renameTo(target)) {
                    val (f, b) = scan(listOf(target))
                    processedItems += f
                    processedBytes += b
                    publish()
                    continue
                }
                copyRecursive(srcFile, target, move)
            }
        }

        /** Папка [dir] совпадает с [root] или лежит внутри неё (с учётом регистра FUSE и симлинков). */
        private fun isInside(dir: File, root: File): Boolean {
            val d = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
            val r = runCatching { root.canonicalPath }.getOrDefault(root.absolutePath)
            if (d == r || d.startsWith("$r/")) return true
            // На FUSE (/storage/emulated) имена регистронезависимы — сверяем inode по цепочке родителей.
            var p: File? = dir
            while (p != null) {
                if (sameFile(p, root)) return true
                p = p.parentFile
            }
            return false
        }

        private suspend fun copyRecursive(src: File, dst: File, move: Boolean) {
            currentCoroutineContext().ensureActive()
            val parent = dst.parentFile
            if (SafAccess.needsSaf(context, src.path) || (parent != null && SafAccess.needsSaf(context, parent.path))) {
                copyAcrossSaf(src, dst, move)
                return
            }
            currentName = src.name
            // Закрытое для приложения место (/data, системные разделы): копирование оттуда отключено —
            // такие файлы можно только просматривать.
            if (client.wexmanager.core.fs.RootShell.enabled && !src.canRead()) {
                failures += "${src.name}: закрытая папка — только просмотр"
                processedItems++
                publish()
                return
            }
            if (isSymlink(src)) {
                val linkTarget = runCatching { Os.readlink(src.path) }.getOrNull()
                if (linkTarget != null) {
                    if (dst.exists() || isSymlink(dst)) {
                        val d = askConflict(LocalFileSystem.stat(src.path), LocalFileSystem.stat(dst.path))
                        if (d.choice == ConflictChoice.Skip || d.choice == ConflictChoice.Merge) { processedItems++; return }
                        if (d.choice == ConflictChoice.KeepBoth) {
                            val alt = File(dst.parentFile, LocalFileSystem.uniqueName(dst.parent!!, dst.name))
                            runCatching { Os.symlink(linkTarget, alt.path) }.onFailure { failures += "${src.name}: ${it.message}" }
                            if (move) src.delete()
                            processedItems++
                            return
                        }
                        if (dst.isDirectory && !isSymlink(dst)) { failures += "${src.name}: на месте ссылки находится папка"; processedItems++; return }
                        dst.delete()
                    }
                    runCatching { Os.symlink(linkTarget, dst.path) }.onFailure { failures += "${src.name}: ${it.message}" }
                    if (move) src.delete()
                    processedItems++
                    publish()
                    return
                }
            }
            if (src.isDirectory) {
                var target = dst
                if (target.exists() && !target.isDirectory) {
                    // Папка против файла: замена только по явному выбору (не из кеша).
                    val d = askConflict(LocalFileSystem.stat(src.path), LocalFileSystem.stat(target.path))
                    when (d.choice) {
                        ConflictChoice.Skip, ConflictChoice.Merge -> { skipTree(src); return }
                        ConflictChoice.KeepBoth -> target = File(target.parentFile, LocalFileSystem.uniqueName(target.parent!!, target.name))
                        ConflictChoice.Replace -> if (!target.delete()) { failures += "${target.name}: не удалось заменить"; skipTree(src); return }
                    }
                } else if (target.exists()) {
                    // Папка против папки: «заменить» = объединить, файлы внутри решаются по отдельности.
                    val d = askConflict(LocalFileSystem.stat(src.path), LocalFileSystem.stat(target.path))
                    when (d.choice) {
                        ConflictChoice.Skip -> { skipTree(src); return }
                        ConflictChoice.KeepBoth -> target = File(target.parentFile, LocalFileSystem.uniqueName(target.parent!!, target.name))
                        ConflictChoice.Replace, ConflictChoice.Merge -> Unit
                    }
                }
                if (!target.exists() && !target.mkdirs()) {
                    failures += "${src.name}: не удалось создать папку"
                    skipTree(src)
                    return
                }
                src.listFiles()?.forEach { child -> copyRecursive(child, File(target, child.name), move) }
                target.setLastModified(src.lastModified())
                if (move && src.list().isNullOrEmpty()) src.delete()
                return
            }
            var target = dst
            var replaceDir = false
            if (target.exists()) {
                val d = askConflict(LocalFileSystem.stat(src.path), LocalFileSystem.stat(target.path))
                when (d.choice) {
                    ConflictChoice.Skip -> {
                        processedItems++
                        processedBytes += src.length()
                        publish()
                        return
                    }
                    ConflictChoice.KeepBoth -> target = File(target.parentFile, LocalFileSystem.uniqueName(target.parent!!, target.name))
                    ConflictChoice.Replace, ConflictChoice.Merge -> {
                        if (target.isDirectory) {
                            // Файл против папки: решение явное для этой пары (такие конфликты не кешируются).
                            // Папку убираем только после того, как новый файл готов (см. ниже).
                            replaceDir = true
                        } else if (sameFile(src, target)) {
                            processedItems++
                            processedBytes += src.length()
                            return
                        }
                    }
                }
            }
            /** Подменяет папку [target] готовым файлом [ready]: папка откладывается и удаляется только после успеха. */
            fun swapDirWith(ready: File) {
                val old = File(target.parentFile, ".wex-${java.util.UUID.randomUUID().toString().take(12)}.old")
                if (!target.renameTo(old)) throw IOException("не удалось заменить папку")
                if (!ready.renameTo(target)) {
                    old.renameTo(target)
                    throw IOException("не удалось записать файл")
                }
                if (!LocalFileSystem.deleteTree(old)) failures += "${target.name}: прежняя папка удалена не полностью"
            }
            if (move && !replaceDir && src.renameTo(target)) {
                processedBytes += target.length()
            } else if (move && replaceDir && runCatching { swapDirWith(src) }.isSuccess) {
                processedBytes += target.length()
            } else {
                // Пишем во временный файл и подменяем атомарно: при сбое старая версия цела.
                // Короткое имя: длинное исходное + суффикс превысило бы предел длины имени (255 байт).
                val tmp = File(target.parentFile, ".wex-${java.util.UUID.randomUUID().toString().take(12)}.part")
                try {
                    copyFile(src, tmp)
                    if (replaceDir) swapDirWith(tmp)
                    else if (!tmp.renameTo(target)) {
                        target.delete()
                        if (!tmp.renameTo(target)) throw IOException("не удалось записать файл")
                    }
                    if (move && !src.delete()) failures += "${src.name}: не удалось удалить исходный файл"
                } catch (e: CancellationException) {
                    tmp.delete()
                    throw e
                } catch (e: IOException) {
                    tmp.delete()
                    failures += "${src.name}: ${e.message}"
                }
            }
            processedItems++
            publish()
        }

        private suspend fun skipTree(dir: File) {
            val (f, b) = scan(listOf(dir))
            processedItems += f
            processedBytes += b
            publish()
        }

        private fun makeDirectory(dir: File): Boolean {
            if (dir.isDirectory) return true
            val parent = dir.parentFile
            if (parent != null && SafAccess.needsSaf(context, parent.path)) return SafAccess.mkdir(context, dir.path)
            return dir.mkdirs()
        }

        private fun nodeExists(file: File): Boolean =
            file.exists() || (SafAccess.needsSaf(context, file.path) && SafAccess.stat(context, file.path) != null)

        private fun nodeIsDirectory(file: File): Boolean {
            if (SafAccess.needsSaf(context, file.path)) return SafAccess.stat(context, file.path)?.isDirectory == true
            return file.isDirectory && !isSymlink(file)
        }

        private fun nodeItem(file: File): FileItem =
            if (SafAccess.needsSaf(context, file.path)) SafAccess.stat(context, file.path) ?: LocalFileSystem.stat(file.path)
            else LocalFileSystem.stat(file.path)

        private fun removeNode(file: File): Boolean =
            if (SafAccess.needsSaf(context, file.path)) SafAccess.delete(context, file.path) else {
                if (file.isDirectory && !isSymlink(file)) LocalFileSystem.deleteTree(file) else file.delete() || !file.exists()
            }

        private fun childNames(dir: File): List<String> =
            if (SafAccess.needsSaf(context, dir.path)) SafAccess.list(context, dir.path).map { it.name }
            else dir.list()?.toList().orEmpty()

        private suspend fun copyAcrossSaf(src: File, dst: File, move: Boolean) {
            currentName = src.name
            val srcDir = nodeIsDirectory(src)
            if (srcDir) {
                var target = dst
                if (nodeExists(target) && !nodeIsDirectory(target)) {
                    val d = askConflict(nodeItem(src), nodeItem(target))
                    when (d.choice) {
                        ConflictChoice.Skip, ConflictChoice.Merge -> { skipSaf(src); return }
                        ConflictChoice.KeepBoth -> target = File(target.parentFile, freeName(target.parentFile ?: target, target.name))
                        ConflictChoice.Replace -> if (!removeNode(target)) {
                            failures += "${target.name}: не удалось заменить"
                            skipSaf(src)
                            return
                        }
                    }
                } else if (nodeExists(target)) {
                    val d = askConflict(nodeItem(src), nodeItem(target))
                    when (d.choice) {
                        ConflictChoice.Skip -> { skipSaf(src); return }
                        ConflictChoice.KeepBoth -> target = File(target.parentFile, freeName(target.parentFile ?: target, target.name))
                        ConflictChoice.Replace, ConflictChoice.Merge -> Unit
                    }
                }
                if (!nodeExists(target) && !makeDirectory(target)) {
                    failures += "${src.name}: не удалось создать папку"
                    skipSaf(src)
                    return
                }
                for (name in childNames(src)) copyRecursive(File(src, name), File(target, name), move)
                if (move && !removeNode(src)) failures += "${src.name}: не удалось удалить исходную папку"
                return
            }
            var target = dst
            if (nodeExists(target)) {
                val d = askConflict(nodeItem(src), nodeItem(target))
                when (d.choice) {
                    ConflictChoice.Skip -> {
                        processedItems++
                        processedBytes += nodeItem(src).size
                        publish()
                        return
                    }
                    ConflictChoice.KeepBoth -> target = File(target.parentFile, freeName(target.parentFile ?: target, target.name))
                    ConflictChoice.Replace, ConflictChoice.Merge -> if (nodeIsDirectory(target) && !removeNode(target)) {
                        failures += "${target.name}: не удалось заменить папку"
                        processedItems++
                        return
                    }
                }
            }
            try {
                copyBytes(src, target)
                if (move && !removeNode(src)) failures += "${src.name}: не удалось удалить исходный файл"
            } catch (e: CancellationException) {
                if (SafAccess.needsSaf(context, target.path)) SafAccess.delete(context, target.path) else target.delete()
                throw e
            } catch (e: IOException) {
                if (SafAccess.needsSaf(context, target.path)) SafAccess.delete(context, target.path)
                failures += "${src.name}: ${e.message}"
            }
            processedItems++
            publish()
        }

        private suspend fun copyBytes(src: File, dst: File) {
            val ctx = currentCoroutineContext()
            val onBytes: (Long) -> Unit = { n ->
                if (!ctx.isActive) throw CancellationException()
                processedBytes += n
                publish()
            }
            val parent = dst.parentFile
            if (parent != null && SafAccess.needsSaf(context, parent.path)) {
                SafAccess.openOutput(context, dst.path).use { output ->
                    if (SafAccess.needsSaf(context, src.path)) SafAccess.openInput(context, src.path).use { SafAccess.pump(it, output, onBytes) }
                    else src.inputStream().use { SafAccess.pump(it, output, onBytes) }
                }
                return
            }
            parent?.mkdirs()
            val tmp = File(parent, ".wex-${java.util.UUID.randomUUID().toString().take(12)}.part")
            try {
                if (SafAccess.needsSaf(context, src.path)) {
                    SafAccess.openInput(context, src.path).use { input -> tmp.outputStream().use { SafAccess.pump(input, it, onBytes) } }
                } else {
                    copyFile(src, tmp)
                }
                if (!tmp.renameTo(dst)) {
                    dst.delete()
                    if (!tmp.renameTo(dst)) throw IOException("не удалось записать файл")
                }
            } catch (e: Exception) {
                tmp.delete()
                throw e
            }
        }

        private suspend fun skipSaf(dir: File) {
            val (files, bytes) = scan(listOf(dir))
            processedItems += files
            processedBytes += bytes
            publish()
        }

        private fun freeName(dir: File, name: String): String {
            if (SafAccess.needsSaf(context, dir.path)) {
                val used = SafAccess.list(context, dir.path).mapTo(HashSet()) { it.name }
                if (name !in used) return name
                val dot = name.lastIndexOf('.')
                val (base, ext) = if (dot > 0) name.substring(0, dot) to name.substring(dot) else name to ""
                var i = 1
                while ("$base ($i)$ext" in used) i++
                return "$base ($i)$ext"
            }
            return LocalFileSystem.uniqueName(dir.path, name)
        }

        private suspend fun copyFile(src: File, dst: File) {
            FileInputStream(src).channel.use { input ->
                FileOutputStream(dst).channel.use { output ->
                    val size = input.size()
                    var pos = 0L
                    val chunk = 4L * 1024 * 1024
                    while (pos < size) {
                        currentCoroutineContext().ensureActive()
                        val n = input.transferTo(pos, minOf(chunk, size - pos), output)
                        if (n <= 0) break
                        pos += n
                        processedBytes += n
                        publish()
                    }
                }
            }
            dst.setLastModified(src.lastModified())
            if (src.canExecute()) dst.setExecutable(true, false)
        }

        suspend fun deleteItems(items: List<FileItem>) {
            status = TaskStatus.Preparing
            publish(force = true)
            totalItems = countNodes(items.map { File(it.path) })
            status = TaskStatus.Running
            for (item in items) {
                deleteRecursive(File(item.path))
            }
        }

        private suspend fun deleteRecursive(f: File) {
            currentCoroutineContext().ensureActive()
            currentName = f.name
            if (SafAccess.needsSaf(context, f.path)) {
                // deleteDocument снимает всё дерево сразу, а прогресс считался по каждому узлу.
                val nodes = countNodes(listOf(f))
                if (!SafAccess.delete(context, f.path)) failures += "${f.name}: не удалось удалить"
                processedItems += nodes
                publish()
                return
            }
            // Элемент из закрытой папки: приложение его не видит, «успешное» удаление было бы ложным.
            if (client.wexmanager.core.fs.RootShell.enabled && f.parentFile?.canRead() == false) {
                failures += "${f.name}: закрытая папка — только просмотр"
                processedItems++
                publish()
                return
            }
            if (f.isDirectory && !isSymlink(f)) {
                f.listFiles()?.forEach { deleteRecursive(it) }
            }
            if (!f.delete() && f.exists()) failures += "${f.name}: не удалось удалить"
            processedItems++
            publish()
        }

        suspend fun trashItems(items: List<FileItem>) {
            totalItems = items.size
            status = TaskStatus.Running
            for (item in items) {
                currentCoroutineContext().ensureActive()
                currentName = item.name
                val file = File(item.path)
                val result = runCatching {
                    if (!SafAccess.needsSaf(context, file.path)) trash.moveToTrash(file)
                    else trash.moveToTrash(file) { _, dest ->
                        SafAccess.copyTreeBlocking(context, file.path, dest) { n ->
                            processedBytes += n
                            publish()
                        }
                        true
                    }?.also {
                        if (!SafAccess.delete(context, file.path)) failures += "${item.name}: копия в корзине, исходный файл остался"
                    }
                }
                result.getOrNull()?.let { trashed += it }
                // Никогда не удаляем молча: если корзина недоступна — сообщаем, файл остаётся на месте.
                if (result.getOrNull() == null) {
                    failures += "${item.name}: не удалось переместить в корзину" +
                        (result.exceptionOrNull()?.message?.let { " ($it)" } ?: " — удалите без корзины")
                }
                processedItems++
                publish()
            }
        }
    }

    companion object {
        fun isSymlink(f: File): Boolean =
            runCatching { OsConstants.S_ISLNK(Os.lstat(f.path).st_mode) }.getOrDefault(false)

        /** Один и тот же файл под разными путями (регистр на FUSE, симлинки, bind-mount). */
        fun sameFile(a: File, b: File): Boolean = runCatching {
            val sa = Os.stat(a.path)
            val sb = Os.stat(b.path)
            sa.st_dev == sb.st_dev && sa.st_ino == sb.st_ino
        }.getOrDefault(false)
    }
}
