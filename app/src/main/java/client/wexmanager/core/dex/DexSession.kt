package client.wexmanager.core.dex

import com.tim.dex.model.DexFile
import com.tim.dex.parser.DexReader
import com.tim.dsmali.asm.TreeToDex
import com.tim.dsmali.model.SmaliInsn
import com.tim.dex.writer.DexWriter
import com.tim.dsmali.Smali
import com.tim.dsmali.io.SmaliParser
import com.tim.dsmali.io.SmaliWriter
import com.tim.dsmali.model.SmaliClass
import com.tim.dsmali.model.SmaliTree
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/** Класс в DEX: дескриптор `Lpkg/Name;`, из какого DEX и что за тип. */
data class DexClassInfo(
    val descriptor: String,
    val dexIndex: Int,
    val kind: DexClassKind,
    val methods: Int,
    val fields: Int,
) {
    /** `pkg/sub` (пустой для корня). */
    val packagePath: String get() = descriptor.removePrefix("L").removeSuffix(";").substringBeforeLast('/', "")

    /** `Name$Inner`. */
    val simpleName: String get() = descriptor.removePrefix("L").removeSuffix(";").substringAfterLast('/')

    /** `pkg.sub.Name$Inner`. */
    val javaName: String get() = descriptor.removePrefix("L").removeSuffix(";").replace('/', '.')
}

enum class DexClassKind { Class, Interface, Enum, Annotation, Abstract }

/** Один DEX сеанса: где лежит и его дерево smali. */
class DexUnit(val name: String, val tree: SmaliTree, val originalSize: Long) {
    /** Классы, добавленные или изменённые с последнего сохранения. */
    internal val changed = linkedSetOf<String>()

    /** Удалённые классы (были в исходном DEX). */
    internal val removed = linkedSetOf<String>()
}

/**
 * Сеанс DEX-редактора (как Dex Editor в MT Manager) на clazzpatcher: DEX разбирается в дерево smali,
 * класс правится текстом, дерево собирается обратно в DEX и записывается в файл или в APK.
 * [source] — `.dex` или APK/ZIP; [entries] — какие DEX брать из архива (пусто — сам файл как DEX).
 */
class DexSession private constructor(
    val source: File,
    val entries: List<String>,
    val units: List<DexUnit>,
) {
    private val byDescriptor = HashMap<String, Pair<Int, SmaliClass>>()

    init {
        units.forEachIndexed { i, u -> u.tree.classes.forEach { byDescriptor[it.descriptor] = i to it } }
    }

    /** Изменённые DEX (индексы [units]). */
    val modifiedUnits: MutableSet<Int> = linkedSetOf()

    /** Изменённые классы — для отметки в списке. */
    val modifiedClasses: MutableSet<String> = linkedSetOf()

    val isArchive: Boolean get() = entries.isNotEmpty()

    fun classes(): List<DexClassInfo> = units.flatMapIndexed { i, u -> u.tree.classes.map { info(it, i) } }

    fun find(descriptor: String): SmaliClass? = byDescriptor[descriptor]?.second

    fun dexIndexOf(descriptor: String): Int? = byDescriptor[descriptor]?.first

    /** Текст класса в синтаксисе baksmali. */
    fun smali(descriptor: String): String = SmaliWriter.write(find(descriptor) ?: throw IOException("Класс $descriptor не найден"))

    /**
     * Заменяет класс разобранным текстом. Класс сразу проверяется сборкой в отдельный DEX:
     * ошибку в инструкции видно при сохранении класса, а не всего DEX.
     * Дескриптор в тексте может отличаться (переименование) — старый класс тогда удаляется.
     */
    fun apply(oldDescriptor: String, text: String): DexClassInfo {
        val cls = try {
            SmaliParser.parseClass(text) ?: throw SmaliEditException("В тексте нет класса (.class)")
        } catch (e: IllegalArgumentException) {
            throw SmaliEditException(e.message?.removePrefix("SmaliParser: ") ?: "Ошибка разбора")
        }
        try {
            Smali.assemble(SmaliTree(mutableListOf(cls)))
        } catch (e: Exception) {
            throw SmaliEditException(e.message ?: e.javaClass.simpleName)
        }
        val (index, _) = byDescriptor[oldDescriptor] ?: (0 to null)
        val tree = units[index].tree
        if (cls.descriptor != oldDescriptor) {
            if (byDescriptor[cls.descriptor] != null) throw SmaliEditException("Класс ${cls.descriptor} уже есть")
            tree.removeClass(oldDescriptor)
            byDescriptor.remove(oldDescriptor)
            modifiedClasses.remove(oldDescriptor)
        }
        tree.addClass(cls)
        byDescriptor[cls.descriptor] = index to cls
        editGeneration++
        modifiedUnits += index
        modifiedClasses += cls.descriptor
        units[index].apply {
            if (cls.descriptor != oldDescriptor) { changed.remove(oldDescriptor); removed += oldDescriptor }
            changed += cls.descriptor
            removed.remove(cls.descriptor)
        }
        return info(cls, index)
    }

    /** Новый пустой класс в DEX [dexIndex]. */
    fun create(descriptor: String, dexIndex: Int): DexClassInfo {
        require(descriptor.startsWith("L") && descriptor.endsWith(";")) { "Дескриптор вида Lpkg/Name;" }
        if (byDescriptor[descriptor] != null) throw SmaliEditException("Класс $descriptor уже есть")
        val cls = SmaliClass(descriptor, mutableListOf("public"))
        units[dexIndex].tree.addClass(cls)
        units[dexIndex].changed += descriptor
        byDescriptor[descriptor] = dexIndex to cls
        editGeneration++
        modifiedUnits += dexIndex
        modifiedClasses += descriptor
        return info(cls, dexIndex)
    }

    fun delete(descriptor: String) {
        val (index, _) = byDescriptor.remove(descriptor) ?: return
        units[index].tree.removeClass(descriptor)
        units[index].changed.remove(descriptor)
        units[index].removed += descriptor
        editGeneration++
        modifiedUnits += index
        modifiedClasses.remove(descriptor)
    }

    /** Собирает изменённые DEX: имя → байты. [progress] — (готово, всего). */
    fun build(progress: (Int, Int) -> Unit = { _, _ -> }): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        val todo = modifiedUnits.toList()
        todo.forEachIndexed { k, i ->
            progress(k, todo.size)
            val unit = units[i]
            out[unit.name] = DexWriter(partial(unit) ?: Smali.assemble(unit.tree)).buildDex()
        }
        progress(todo.size, todo.size)
        return out
    }

    fun markSaved() {
        modifiedUnits.clear()
        modifiedClasses.clear()
        units.forEach { it.changed.clear(); it.removed.clear() }
    }

    /**
     * Пересборка только изменённых классов поверх исходного DEX (как в MT): нетронутые классы
     * остаются байт в байт. Через текст smali их собирать нельзя без потерь: R8 привязывает номера
     * строк к каждому pc (и внутри длинных инструкций), а `.line` в smali стоит только между
     * инструкциями — такой DEX после полной пересборки вырастал вдвое.
     * null — нужен полный путь (call site, hiddenapi или исходник уже не читается).
     */
    private fun partial(unit: DexUnit): DexFile? {
        val original = runCatching { readOriginal(unit.name) }.getOrNull() ?: return null
        if (original.hiddenApiClassDataItems.isNotEmpty() || original.callSiteIds.isNotEmpty()) return null
        val drop = unit.changed + unit.removed
        val kept = original.classDefs.filter { cd ->
            original.strings[original.typeIds[cd.classIdx].descriptorIdx] !in drop
        }
        val classes = unit.changed.mapNotNull { d -> unit.tree.findClass(d) }
        if (classes.any { c -> c.methods.any { m -> m.body.any { it is SmaliInsn && it.text.startsWith("invoke-custom") } } }) return null
        return TreeToDex().build(SmaliTree(classes.toMutableList()), original.copy(classDefs = kept))
    }

    /** Исходный DEX из источника (на диске он не меняется до записи результата). */
    private fun readOriginal(name: String): DexFile = DexReader().read(readOriginalBytes(name))

    private fun readOriginalBytes(name: String): ByteArray =
        if (entries.isEmpty()) source.readBytes() else ZipFile(source).use { z ->
            val e = z.getEntry(name) ?: throw IOException("В архиве нет $name")
            z.getInputStream(e).use { it.readBytes() }
        }

    /** Ключ состояния DEX класса: меняется после каждой правки или сохранения (для кеша декомпилятора). */
    fun dexKeyFor(descriptor: String): Any? {
        val index = dexIndexOf(descriptor) ?: return null
        return Triple(index, if (index in modifiedUnits) editGeneration else -1, source.lastModified())
    }

    /** Растёт при каждой правке дерева. */
    private var editGeneration = 0

    /** DEX, в котором лежит класс, — с несохранёнными правками (для декомпиляции в Java). */
    fun dexBytesFor(descriptor: String): ByteArray {
        val index = dexIndexOf(descriptor) ?: throw IOException("Класс $descriptor не найден")
        val unit = units[index]
        return if (index in modifiedUnits) DexWriter(partial(unit) ?: Smali.assemble(unit.tree)).buildDex()
        else readOriginalBytes(unit.name)
    }

    private fun info(c: SmaliClass, dexIndex: Int): DexClassInfo {
        val flags = c.accessFlags
        val kind = when {
            "annotation" in flags -> DexClassKind.Annotation
            "interface" in flags -> DexClassKind.Interface
            "enum" in flags -> DexClassKind.Enum
            "abstract" in flags -> DexClassKind.Abstract
            else -> DexClassKind.Class
        }
        return DexClassInfo(c.descriptor, dexIndex, kind, c.methods.size, c.fields.size)
    }

    companion object {
        private val DEX_NAME = Regex("classes\\d*\\.dex")

        /** DEX-записи архива в порядке classes, classes2, … */
        fun dexEntries(apk: File): List<String> = ZipFile(apk).use { z ->
            z.entries().asSequence().map { it.name }.filter { DEX_NAME.matches(it) }
                .sortedBy { it.removePrefix("classes").removeSuffix(".dex").toIntOrNull() ?: 1 }.toList()
        }

        /** Открывает DEX-файл или выбранные DEX из APK. [progress] — (готово, всего). */
        fun open(source: File, entries: List<String>, progress: (Int, Int) -> Unit = { _, _ -> }): DexSession {
            val units = ArrayList<DexUnit>()
            if (entries.isEmpty()) {
                progress(0, 1)
                val bytes = source.readBytes()
                units += DexUnit(source.name, Smali.disassemble(read(bytes, source.name)), bytes.size.toLong())
            } else {
                ZipFile(source).use { z ->
                    entries.forEachIndexed { k, name ->
                        progress(k, entries.size)
                        val e = z.getEntry(name) ?: throw IOException("В архиве нет $name")
                        val bytes = z.getInputStream(e).use { it.readBytes() }
                        units += DexUnit(name, Smali.disassemble(read(bytes, name)), bytes.size.toLong())
                    }
                }
            }
            progress(units.size, units.size)
            return DexSession(source, entries, units)
        }

        private fun read(bytes: ByteArray, name: String) = try {
            DexReader().read(bytes)
        } catch (e: Exception) {
            throw IOException("$name: не DEX или повреждён (${e.message ?: e.javaClass.simpleName})")
        }
    }
}

class SmaliEditException(message: String) : IOException(message)
