package client.wexmanager.core.apk

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Таблица ресурсов APK (`resources.arsc`): разбор, правка значений и перевод строк — как редактор
 * ARSC и «режим перевода» MT Manager. Устроена по ResourceTypes.h (AOSP):
 * таблица → глобальный пул строк → пакеты (пулы имён типов и ключей, typeSpec и type по конфигурациям).
 *
 * Правка значения меняет 8-байтовый Res_value сущности на месте. Новый текст строки дописывается в
 * конец глобального пула, а не правит старую строку: aapt2 делит одну строку между всеми ресурсами
 * с таким же текстом, и правка «на месте» задела бы чужие. Перевод добавляет блок type с новой
 * языковой конфигурацией сразу за блоками этого типа.
 */
class ArscTable private constructor(
    private val tableHeader: ByteArray,
    val strings: StringPool,
    val packages: List<ArscPackage>,
    private val trailing: List<ByteArray>,
) {
    /** Сериализация: пул пересобирается, пакеты — из своих блоков. */
    fun encode(): ByteArray {
        val pool = strings.encode()
        val pkgs = packages.map { it.encode() }
        val size = tableHeader.size + pool.size + pkgs.sumOf { it.size } + trailing.sumOf { it.size }
        val out = ByteArrayOutputStream(size)
        val header = tableHeader.copyOf()
        le(header).putInt(4, size)
        out.write(header)
        out.write(pool)
        pkgs.forEach { out.write(it) }
        trailing.forEach { out.write(it) }
        return out.toByteArray()
    }

    /** Все ресурсы типа [typeName] во всех конфигурациях пакета [pkg]. */
    fun typeNames(pkg: ArscPackage): List<String> = pkg.typeNames

    /** Значение сущности как текст (строка — сама строка, ссылка — @0x7f…). */
    fun format(v: ResValue): String = v.format(strings)

    /**
     * Новое простое значение [entry]. Для строк текст дописывается в пул.
     * @throws IllegalArgumentException — текст не подходит под тип.
     */
    fun setValue(entry: ArscEntry, text: String) {
        require(!entry.complex) { "Составное значение (bag) так не правится" }
        val type = entry.value.type
        val (newType, data) = when (type) {
            ResValue.TYPE_STRING -> ResValue.TYPE_STRING to strings.add(text)
            else -> ResValue.parse(type, text)
        }
        entry.chunk.writeValue(entry, newType, data)
    }

    /**
     * Перевод строк типа `string` пакета [pkg] на язык [locale] (`ru`, `pt-rBR`, `b+sr+Latn`):
     * [translations] — индекс сущности → текст. Существующие строки этого языка заменяются.
     */
    fun translate(pkg: ArscPackage, locale: String, translations: Map<Int, String>) {
        if (translations.isEmpty()) return
        val typeId = pkg.typeIdOf("string") ?: throw IOException("В пакете нет строк")
        val base = pkg.types(typeId).firstOrNull { it.config.isDefault() } ?: pkg.types(typeId).first()
        val target = pkg.types(typeId).firstOrNull { it.config.qualifier() == locale && it.config.onlyLocale() }
        val values = LinkedHashMap<Int, Pair<Int, Int>>() // индекс → (тип, данные)
        target?.entries?.forEach { e -> if (e != null && !e.complex) values[e.index] = e.value.type to e.value.data }
        for ((index, text) in translations) values[index] = ResValue.TYPE_STRING to strings.add(text)
        val keys = HashMap<Int, Int>()
        for (e in base.entries) if (e != null) keys[e.index] = e.key
        target?.entries?.forEach { e -> if (e != null) keys[e.index] = e.key }
        val count = pkg.specEntryCount(typeId) ?: base.entries.size
        val chunk = TypeChunk.build(typeId, count, base.config.withLocale(locale), values.filterKeys { it in keys && it < count }.mapValues { (i, v) -> Triple(keys.getValue(i), v.first, v.second) })
        pkg.replaceOrAppend(target, chunk)
        pkg.markLocaleDependent(typeId, values.keys)
    }

    companion object {
        const val RES_TABLE_TYPE = 0x0002
        const val RES_STRING_POOL_TYPE = 0x0001
        const val RES_TABLE_PACKAGE_TYPE = 0x0200
        const val RES_TABLE_TYPE_TYPE = 0x0201
        const val RES_TABLE_TYPE_SPEC_TYPE = 0x0202

        fun parse(bytes: ByteArray): ArscTable {
            val b = le(bytes)
            if (bytes.size < 12 || b.getShort(0).toInt() and 0xFFFF != RES_TABLE_TYPE) throw IOException("Не таблица ресурсов (resources.arsc)")
            val headerSize = b.getShort(2).toInt() and 0xFFFF
            val size = minOf(b.getInt(4), bytes.size)
            var pos = headerSize
            var pool: StringPool? = null
            val packages = ArrayList<ArscPackage>()
            val trailing = ArrayList<ByteArray>()
            while (pos + 8 <= size) {
                val type = b.getShort(pos).toInt() and 0xFFFF
                val chunkSize = b.getInt(pos + 4)
                if (chunkSize < 8 || pos + chunkSize > size) throw IOException("Повреждённый блок 0x${type.toString(16)} в таблице")
                when {
                    type == RES_STRING_POOL_TYPE && pool == null -> pool = StringPool.parse(bytes, pos)
                    type == RES_TABLE_PACKAGE_TYPE -> packages += ArscPackage.parse(bytes, pos)
                    else -> trailing += bytes.copyOfRange(pos, pos + chunkSize)
                }
                pos += chunkSize
            }
            return ArscTable(bytes.copyOfRange(0, headerSize), pool ?: throw IOException("Нет глобального пула строк"), packages, trailing)
        }

        internal fun le(bytes: ByteArray): ByteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    }
}

/**
 * ResStringPool: строки и стили. Стиль i относится к строке i (стилей не больше, чем строк):
 * список отрезков (имя тега — индекс строки этого же пула, первый и последний символ).
 */
class StringPool private constructor(
    private val list: MutableList<String>,
    private var flags: Int,
    private val styles: MutableList<IntArray>,
    /** Хвост после последнего стиля (aapt дописывает два маркера END) — пишется как был. */
    private val styleTail: ByteArray,
) {
    val size: Int get() = list.size
    val utf8: Boolean get() = flags and UTF8_FLAG != 0

    operator fun get(i: Int): String? = list.getOrNull(i)

    /** Новая строка в конце пула (сортировку пул после этого не гарантирует). */
    fun add(s: String): Int {
        list += s
        flags = flags and SORTED_FLAG.inv()
        return list.size - 1
    }

    /** Отрезки стиля строки [i]: (тег, первый символ, последний символ); пусто — без оформления. */
    fun spans(i: Int): List<Triple<String, Int, Int>> {
        val st = styles.getOrNull(i) ?: return emptyList()
        return (st.indices step 3).map { k -> Triple(list.getOrNull(st[k]) ?: "", st[k + 1], st[k + 2]) }
    }

    /** Строка с оформлением: стили до неё дополняются пустыми (стиль i всегда относится к строке i). */
    fun addStyled(s: String, spans: List<Triple<String, Int, Int>>): Int {
        if (spans.isEmpty()) return add(s)
        val names = spans.map { (tag, _, _) -> list.indexOf(tag).takeIf { it >= 0 } ?: add(tag) }
        val idx = add(s)
        while (styles.size < idx) styles += IntArray(0)
        styles += IntArray(spans.size * 3).also { a -> spans.forEachIndexed { k, (_, first, last) -> a[3 * k] = names[k]; a[3 * k + 1] = first; a[3 * k + 2] = last } }
        return idx
    }

    fun encode(): ByteArray {
        val data = ByteArrayOutputStream()
        val offsets = IntArray(list.size)
        for ((i, s) in list.withIndex()) {
            offsets[i] = data.size()
            if (utf8) {
                val bytes = s.toByteArray(Charsets.UTF_8)
                writeLen8(data, s.length)
                writeLen8(data, bytes.size)
                data.write(bytes)
                data.write(0)
            } else {
                writeLen16(data, s.length)
                for (c in s) { data.write(c.code and 0xFF); data.write(c.code ushr 8) }
                data.write(0); data.write(0)
            }
        }
        while (data.size() % 4 != 0) data.write(0)
        val styleBytes = ArscTable.le(ByteArray(styles.sumOf { 4 * it.size + 4 } + if (styles.isEmpty()) 0 else maxOf(styleTail.size, 8)))
        val styleOffsets = IntArray(styles.size)
        styles.forEachIndexed { i, st ->
            styleOffsets[i] = styleBytes.position()
            st.forEach { styleBytes.putInt(it) }
            styleBytes.putInt(-1) // ResStringPool_span::END
        }
        if (styles.isNotEmpty()) {
            if (styleTail.size >= 8) styleBytes.put(styleTail) else { styleBytes.putInt(-1); styleBytes.putInt(-1) }
        }
        val styleData = styleBytes.array()
        val headerSize = 28
        val stringsStart = headerSize + 4 * list.size + 4 * styleOffsets.size
        val stylesStart = if (styleOffsets.isEmpty()) 0 else stringsStart + data.size()
        val size = stringsStart + data.size() + styleData.size
        val buf = ArscTable.le(ByteArray(size))
        buf.putShort(ArscTable.RES_STRING_POOL_TYPE.toShort()).putShort(headerSize.toShort()).putInt(size)
        buf.putInt(list.size).putInt(styleOffsets.size).putInt(flags).putInt(stringsStart).putInt(stylesStart)
        offsets.forEach { buf.putInt(it) }
        styleOffsets.forEach { buf.putInt(it) }
        buf.put(data.toByteArray())
        buf.put(styleData)
        return buf.array()
    }

    private fun writeLen8(out: ByteArrayOutputStream, n: Int) {
        if (n > 0x7F) out.write(((n shr 8) and 0x7F) or 0x80)
        out.write(n and 0xFF)
    }

    private fun writeLen16(out: ByteArrayOutputStream, n: Int) {
        if (n > 0x7FFF) {
            val hi = ((n shr 16) and 0x7FFF) or 0x8000
            out.write(hi and 0xFF); out.write(hi ushr 8)
        }
        out.write(n and 0xFF); out.write((n ushr 8) and 0xFF)
    }

    companion object {
        const val SORTED_FLAG = 0x1
        const val UTF8_FLAG = 0x100

        fun parse(bytes: ByteArray, start: Int): StringPool {
            val b = ArscTable.le(bytes)
            val headerSize = b.getShort(start + 2).toInt() and 0xFFFF
            val size = b.getInt(start + 4)
            val count = b.getInt(start + 8)
            val styleCount = b.getInt(start + 12)
            val flags = b.getInt(start + 16)
            val stringsStart = b.getInt(start + 20)
            val stylesStart = b.getInt(start + 24)
            if (count < 0 || styleCount < 0 || start + headerSize + 4L * (count + styleCount) > bytes.size) throw IOException("Повреждённый пул строк")
            val utf8 = flags and UTF8_FLAG != 0
            val list = ArrayList<String>(count)
            for (i in 0 until count) {
                val off = start + stringsStart + b.getInt(start + headerSize + 4 * i)
                list += if (utf8) readUtf8(bytes, off) else readUtf16(b, off)
            }
            val styles = ArrayList<IntArray>(styleCount)
            var tail = ByteArray(0)
            if (styleCount > 0 && stylesStart > 0) {
                val end = start + size
                var last = start + stylesStart
                for (i in 0 until styleCount) {
                    var p = start + stylesStart + b.getInt(start + headerSize + 4 * count + 4 * i)
                    val spans = ArrayList<Int>()
                    while (p + 4 <= end && b.getInt(p) != -1) {
                        if (p + 12 > end) throw IOException("Повреждённый стиль строки $i")
                        spans += b.getInt(p); spans += b.getInt(p + 4); spans += b.getInt(p + 8)
                        p += 12
                    }
                    styles += spans.toIntArray()
                    last = maxOf(last, p + 4)
                }
                tail = bytes.copyOfRange(minOf(last, end), end)
            }
            return StringPool(list, flags, styles, tail)
        }

        private fun readUtf8(bytes: ByteArray, at: Int): String {
            var p = at
            fun len(): Int {
                val a = bytes[p++].toInt() and 0xFF
                return if (a and 0x80 != 0) ((a and 0x7F) shl 8) or (bytes[p++].toInt() and 0xFF) else a
            }
            len() // число символов
            val n = len()
            return String(bytes, p, n, Charsets.UTF_8)
        }

        private fun readUtf16(b: ByteBuffer, at: Int): String {
            var p = at
            var n = b.getShort(p).toInt() and 0xFFFF
            p += 2
            if (n and 0x8000 != 0) {
                n = ((n and 0x7FFF) shl 16) or (b.getShort(p).toInt() and 0xFFFF)
                p += 2
            }
            val chars = CharArray(n) { b.getChar(p + 2 * it) }
            return String(chars)
        }
    }
}

/** Значение Res_value: тип и 32 бита данных. */
data class ResValue(val type: Int, val data: Int) {
    fun format(pool: StringPool): String = when (type) {
        TYPE_NULL -> if (data == 0) "@null" else "@empty"
        TYPE_REFERENCE, TYPE_DYNAMIC_REFERENCE -> "@0x" + hex8(data)
        TYPE_ATTRIBUTE, TYPE_DYNAMIC_ATTRIBUTE -> "?0x" + hex8(data)
        TYPE_STRING -> pool[data] ?: "@string/#$data"
        TYPE_FLOAT -> java.lang.Float.intBitsToFloat(data).toString()
        TYPE_DIMENSION -> complexText(data, listOf("px", "dp", "sp", "pt", "in", "mm"), 1f)
        TYPE_FRACTION -> complexText(data, listOf("%", "%p"), 100f)
        TYPE_INT_DEC -> data.toString()
        TYPE_INT_HEX -> "0x" + Integer.toHexString(data)
        TYPE_INT_BOOLEAN -> if (data != 0) "true" else "false"
        TYPE_INT_COLOR_ARGB8, TYPE_INT_COLOR_ARGB4 -> "#" + hex8(data).uppercase()
        TYPE_INT_COLOR_RGB8, TYPE_INT_COLOR_RGB4 -> "#" + hex8(data).substring(2).uppercase()
        else -> "0x" + Integer.toHexString(data) + " (тип 0x" + Integer.toHexString(type) + ")"
    }

    val typeName: String get() = when (type) {
        TYPE_STRING -> "строка"
        TYPE_REFERENCE, TYPE_DYNAMIC_REFERENCE -> "ссылка"
        TYPE_ATTRIBUTE, TYPE_DYNAMIC_ATTRIBUTE -> "атрибут"
        TYPE_FLOAT -> "дробное"
        TYPE_DIMENSION -> "размер"
        TYPE_FRACTION -> "доля"
        TYPE_INT_DEC, TYPE_INT_HEX -> "целое"
        TYPE_INT_BOOLEAN -> "логическое"
        in TYPE_INT_COLOR_ARGB8..TYPE_INT_COLOR_RGB4 -> "цвет"
        else -> "тип 0x" + Integer.toHexString(type)
    }

    companion object {
        const val TYPE_NULL = 0x00
        const val TYPE_REFERENCE = 0x01
        const val TYPE_ATTRIBUTE = 0x02
        const val TYPE_STRING = 0x03
        const val TYPE_FLOAT = 0x04
        const val TYPE_DIMENSION = 0x05
        const val TYPE_FRACTION = 0x06
        const val TYPE_DYNAMIC_REFERENCE = 0x07
        const val TYPE_DYNAMIC_ATTRIBUTE = 0x08
        const val TYPE_INT_DEC = 0x10
        const val TYPE_INT_HEX = 0x11
        const val TYPE_INT_BOOLEAN = 0x12
        const val TYPE_INT_COLOR_ARGB8 = 0x1c
        const val TYPE_INT_COLOR_RGB8 = 0x1d
        const val TYPE_INT_COLOR_ARGB4 = 0x1e
        const val TYPE_INT_COLOR_RGB4 = 0x1f

        private fun hex8(v: Int) = Integer.toHexString(v).padStart(8, '0')

        private fun complexText(data: Int, units: List<String>, scale: Float): String {
            val radix = (data ushr 4) and 0x3
            val shift = intArrayOf(0, 7, 15, 23)[radix] // дробных бит у radix 23p0, 16p7, 8p15, 0p23
            val value = (data shr 8).toDouble() / (1 shl shift) * scale
            val unit = units.getOrElse(data and 0xF) { "" }
            val text = java.math.BigDecimal(value).setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
            return text + unit
        }

        /** Текст → (тип, данные) для простых типов; тип может смениться (int ↔ hex, цвет #RGB ↔ #ARGB). */
        fun parse(type: Int, raw: String): Pair<Int, Int> {
            val v = raw.trim()
            fun bad(what: String): Nothing = throw IllegalArgumentException("Ожидается $what")
            return when (type) {
                TYPE_INT_BOOLEAN -> when (v.lowercase()) {
                    "true" -> TYPE_INT_BOOLEAN to -1
                    "false" -> TYPE_INT_BOOLEAN to 0
                    else -> bad("true или false")
                }
                TYPE_INT_DEC, TYPE_INT_HEX -> when {
                    v.startsWith("0x", true) -> TYPE_INT_HEX to (v.substring(2).toLongOrNull(16)?.toInt() ?: bad("целое, например 42 или 0x2A"))
                    else -> TYPE_INT_DEC to (v.toIntOrNull() ?: bad("целое, например 42 или 0x2A"))
                }
                TYPE_FLOAT -> TYPE_FLOAT to java.lang.Float.floatToIntBits(v.toFloatOrNull() ?: bad("число, например 1.5"))
                in TYPE_INT_COLOR_ARGB8..TYPE_INT_COLOR_RGB4 -> {
                    val h = v.removePrefix("#")
                    if (h.any { it.digitToIntOrNull(16) == null }) bad("цвет #RGB, #ARGB, #RRGGBB или #AARRGGBB")
                    fun dup(s: String) = s.map { "$it$it" }.joinToString("")
                    when (h.length) {
                        3 -> TYPE_INT_COLOR_RGB4 to (0xFF000000.toInt() or dup(h).toInt(16))
                        4 -> TYPE_INT_COLOR_ARGB4 to dup(h).toLong(16).toInt()
                        6 -> TYPE_INT_COLOR_RGB8 to (0xFF000000.toInt() or h.toInt(16))
                        8 -> TYPE_INT_COLOR_ARGB8 to h.toLong(16).toInt()
                        else -> bad("цвет #RGB, #ARGB, #RRGGBB или #AARRGGBB")
                    }
                }
                TYPE_DIMENSION -> TYPE_DIMENSION to (AxmlEncoder.complex(v, AxmlEncoder.DIMEN_UNITS) ?: bad("размер, например 16dp"))
                TYPE_FRACTION -> TYPE_FRACTION to (AxmlEncoder.complex(v, AxmlEncoder.FRACTION_UNITS) ?: bad("доля, например 50%"))
                TYPE_REFERENCE, TYPE_DYNAMIC_REFERENCE, TYPE_ATTRIBUTE, TYPE_DYNAMIC_ATTRIBUTE -> {
                    val h = v.removePrefix("@").removePrefix("?").removePrefix("0x")
                    type to (h.toLongOrNull(16)?.toInt() ?: bad("ссылка @0x7f010001"))
                }
                else -> bad("значение этого типа")
            }
        }
    }
}

/** Сущность ресурса в одной конфигурации. [index] — номер в типе (младшие 16 бит id). */
class ArscEntry internal constructor(
    val chunk: TypeChunk,
    val index: Int,
    val key: Int,
    val complex: Boolean,
    value: ResValue,
    /** Смещение Res_value.dataType внутри блока (для простых), -1 — у составных. */
    internal val typeOffset: Int,
    internal val dataOffset: Int,
    internal val compact: Boolean,
    /** Пары bag: имя (id атрибута) → значение. */
    val bag: List<Pair<Int, ResValue>>,
    /** Смещение ResTable_entry внутри блока. */
    internal val start: Int = -1,
) {
    var value: ResValue = value
        internal set

    fun resId(pkg: ArscPackage): Int = (pkg.id shl 24) or (chunk.typeId shl 16) or index
}

/** Конфигурация (ResTable_config) — байты как есть + разбор квалификаторов. */
class ResConfig(val bytes: ByteArray) {
    fun isDefault(): Boolean = bytes.drop(4).all { it.toInt() == 0 }

    /** Задан только язык/регион (без плотности, ночи, версии…) — подходит для перевода. */
    fun onlyLocale(): Boolean = bytes.withIndex().all { (i, b) -> i < 4 || i in 8..11 || i in 36..47 || i >= 52 || b.toInt() == 0 }

    fun qualifier(): String = ConfigQualifiers.format(bytes)

    fun locale(): String = ConfigQualifiers.locale(bytes)

    /** Копия с языком [locale] (`ru`, `pt-rBR`, `b+sr+Latn`); остальные квалификаторы сброшены. */
    fun withLocale(locale: String): ResConfig {
        val out = ByteArray(bytes.size)
        System.arraycopy(bytes, 0, out, 0, 4)
        ConfigQualifiers.writeLocale(out, locale)
        return ResConfig(out)
    }
}

/** Блок RES_TABLE_TYPE_TYPE. Правка значения — в [raw] на месте; [entries] — разобранные сущности. */
class TypeChunk internal constructor(internal var raw: ByteArray) {
    val typeId: Int get() = raw[8].toInt() and 0xFF
    val entryCount: Int get() = ArscTable.le(raw).getInt(12)
    val config: ResConfig
    val entries: Array<ArscEntry?>

    init {
        val b = ArscTable.le(raw)
        val headerSize = b.getShort(2).toInt() and 0xFFFF
        val flags = raw[9].toInt() and 0xFF
        val count = b.getInt(12)
        val entriesStart = b.getInt(16)
        val cfgSize = b.getInt(20)
        config = ResConfig(raw.copyOfRange(20, 20 + minOf(cfgSize, headerSize - 20)))
        val sparse = flags and FLAG_SPARSE != 0
        // В разреженном блоке entryCount — число записей, а индексы — до размера типа.
        val slots = if (!sparse) count else (0 until count).maxOfOrNull { (b.getShort(headerSize + 4 * it).toInt() and 0xFFFF) + 1 } ?: 0
        entries = arrayOfNulls(slots)
        val offset16 = flags and FLAG_OFFSET16 != 0
        fun entryAt(index: Int, off: Int) {
            if (off < 0) return
            val p = entriesStart + off
            if (p + 8 > raw.size || index !in entries.indices) return
            val size = b.getShort(p).toInt() and 0xFFFF
            val eflags = b.getShort(p + 2).toInt() and 0xFFFF
            if (eflags and ENTRY_COMPACT != 0) {
                // Компактная запись (aapt2 --enable-compact-entries): key(u16) flags(u16, тип в старшем байте) data(u32)
                val type = eflags ushr 8
                entries[index] = ArscEntry(this, index, size, false, ResValue(type, b.getInt(p + 4)), p + 3, p + 4, true, emptyList(), p)
                return
            }
            val key = b.getInt(p + 4)
            if (eflags and ENTRY_COMPLEX != 0) {
                val parent = b.getInt(p + 8)
                val n = b.getInt(p + 12)
                val bag = ArrayList<Pair<Int, ResValue>>()
                var q = p + size
                repeat(maxOf(0, minOf(n, (raw.size - q) / 12))) {
                    bag += b.getInt(q) to ResValue(raw[q + 7].toInt() and 0xFF, b.getInt(q + 8))
                    q += 12
                }
                entries[index] = ArscEntry(this, index, key, true, ResValue(ResValue.TYPE_REFERENCE, parent), -1, -1, false, bag, p)
            } else {
                val v = p + size
                entries[index] = ArscEntry(this, index, key, false, ResValue(raw[v + 3].toInt() and 0xFF, b.getInt(v + 4)), v + 3, v + 4, false, emptyList(), p)
            }
        }
        val table = headerSize
        when {
            sparse -> for (i in 0 until count) {
                val idx = b.getShort(table + 4 * i).toInt() and 0xFFFF
                val off = (b.getShort(table + 4 * i + 2).toInt() and 0xFFFF) * 4
                entryAt(idx, off)
            }
            offset16 -> for (i in 0 until count) {
                val o = b.getShort(table + 2 * i).toInt() and 0xFFFF
                if (o != 0xFFFF) entryAt(i, o * 4)
            }
            else -> for (i in 0 until count) {
                val o = b.getInt(table + 4 * i)
                if (o != -1) entryAt(i, o)
            }
        }
    }

    internal fun writeValue(entry: ArscEntry, type: Int, data: Int) {
        val b = ArscTable.le(raw)
        raw[entry.typeOffset] = type.toByte()
        b.putInt(entry.dataOffset, data)
        entry.value = ResValue(type, data)
    }

    companion object {
        const val FLAG_SPARSE = 0x01
        const val FLAG_OFFSET16 = 0x02
        const val ENTRY_COMPLEX = 0x0001
        const val ENTRY_COMPACT = 0x0008

        /** Новый блок с простыми значениями: индекс → (ключ, тип, данные). */
        fun build(typeId: Int, entryCount: Int, config: ResConfig, values: Map<Int, Triple<Int, Int, Int>>): TypeChunk {
            val headerSize = 20 + config.bytes.size
            val entriesStart = headerSize + 4 * entryCount
            val size = entriesStart + 16 * values.size
            val b = ArscTable.le(ByteArray(size))
            b.putShort(ArscTable.RES_TABLE_TYPE_TYPE.toShort()).putShort(headerSize.toShort()).putInt(size)
            b.put(typeId.toByte()).put(0).putShort(0).putInt(entryCount).putInt(entriesStart)
            b.put(config.bytes)
            val sorted = values.toSortedMap()
            var off = 0
            val offsets = IntArray(entryCount) { -1 }
            for (i in sorted.keys) { offsets[i] = off; off += 16 }
            offsets.forEach { b.putInt(it) }
            for ((_, v) in sorted) {
                b.putShort(8).putShort(0).putInt(v.first) // ResTable_entry
                b.putShort(8).put(0).put(v.second.toByte()).putInt(v.third) // Res_value
            }
            return TypeChunk(b.array())
        }
    }
}

/** Пакет: шапка, пулы имён типов и ключей и блоки typeSpec/type/прочие (как есть). */
class ArscPackage private constructor(
    private val header: ByteArray,
    val id: Int,
    val name: String,
    val typeStrings: StringPool,
    val keyStrings: StringPool,
    private val typeStringsRaw: ByteArray,
    private val keyStringsRaw: ByteArray,
    private val chunks: MutableList<Any>, // TypeChunk | SpecChunk | ByteArray
) {
    val typeNames: List<String> get() = (0 until typeStrings.size).map { typeStrings[it] ?: "" }

    /** В пул ключей добавлены имена — при записи он пересобирается (иначе пишется как был). */
    internal var keysChanged = false

    fun typeIdOf(name: String): Int? = typeNames.indexOf(name).takeIf { it >= 0 }?.plus(1)

    fun typeName(typeId: Int): String = typeStrings[typeId - 1] ?: "type$typeId"

    fun key(entry: ArscEntry): String = keyStrings[entry.key] ?: "#${entry.key}"

    fun types(typeId: Int): List<TypeChunk> = chunks.filterIsInstance<TypeChunk>().filter { it.typeId == typeId }

    /** Число ресурсов типа (по typeSpec). */
    fun specEntryCount(typeId: Int): Int? = chunks.filterIsInstance<SpecChunk>().firstOrNull { it.typeId == typeId }?.let { ArscTable.le(it.raw).getInt(12) }

    fun allTypes(): List<TypeChunk> = chunks.filterIsInstance<TypeChunk>()

    internal fun replaceOrAppend(old: TypeChunk?, new: TypeChunk) {
        if (old != null) {
            chunks[chunks.indexOf(old)] = new
            return
        }
        val last = chunks.indexOfLast { (it is TypeChunk && it.typeId == new.typeId) || (it is SpecChunk && it.typeId == new.typeId) }
        chunks.add(last + 1, new)
    }

    /** Флаги typeSpec по сущностям (от каких квалификаторов зависит значение). */
    internal fun specFlags(typeId: Int): IntArray? {
        val spec = chunks.filterIsInstance<SpecChunk>().firstOrNull { it.typeId == typeId } ?: return null
        val b = ArscTable.le(spec.raw)
        val headerSize = b.getShort(2).toInt() and 0xFFFF
        return IntArray(b.getInt(12)) { b.getInt(headerSize + 4 * it) }
    }

    /** Флаги из typeSpec части (слияние split): значение зависит от квалификаторов обеих частей. */
    internal fun mergeSpecFlags(typeId: Int, flags: IntArray?) {
        if (flags == null) return
        val spec = chunks.filterIsInstance<SpecChunk>().firstOrNull { it.typeId == typeId } ?: return
        val b = ArscTable.le(spec.raw)
        val headerSize = b.getShort(2).toInt() and 0xFFFF
        for (i in 0 until minOf(b.getInt(12), flags.size)) {
            val p = headerSize + 4 * i
            b.putInt(p, b.getInt(p) or flags[i])
        }
    }

    /** Флаг CONFIG_LOCALE у сущностей в typeSpec: значение теперь зависит от языка. */
    internal fun markLocaleDependent(typeId: Int, indices: Collection<Int>) {
        val spec = chunks.filterIsInstance<SpecChunk>().firstOrNull { it.typeId == typeId } ?: return
        val b = ArscTable.le(spec.raw)
        val headerSize = b.getShort(2).toInt() and 0xFFFF
        val count = b.getInt(12)
        for (i in indices) if (i in 0 until count) {
            val p = headerSize + 4 * i
            b.putInt(p, b.getInt(p) or CONFIG_LOCALE)
        }
    }

    fun encode(): ByteArray {
        val headerSize = header.size
        val body = ByteArrayOutputStream()
        body.write(typeStringsRaw)
        body.write(if (keysChanged) keyStrings.encode() else keyStringsRaw)
        for (c in chunks) when (c) {
            is TypeChunk -> body.write(c.raw)
            is SpecChunk -> body.write(c.raw)
            is ByteArray -> body.write(c)
        }
        val out = header.copyOf()
        val b = ArscTable.le(out)
        b.putInt(4, headerSize + body.size())
        b.putInt(268, headerSize) // typeStrings — сразу за шапкой
        b.putInt(276, headerSize + typeStringsRaw.size) // keyStrings
        return out + body.toByteArray()
    }

    internal class SpecChunk(val raw: ByteArray) {
        val typeId: Int get() = raw[8].toInt() and 0xFF
    }

    companion object {
        const val CONFIG_LOCALE = 0x0004

        fun parse(bytes: ByteArray, start: Int): ArscPackage {
            val b = ArscTable.le(bytes)
            val headerSize = b.getShort(start + 2).toInt() and 0xFFFF
            val size = b.getInt(start + 4)
            val id = b.getInt(start + 8)
            val name = String(CharArray(128) { b.getChar(start + 12 + 2 * it) }).substringBefore('\u0000')
            val typeStringsOff = b.getInt(start + 268)
            val keyStringsOff = b.getInt(start + 276)
            if (typeStringsOff <= 0 || keyStringsOff <= 0) throw IOException("Пакет $name без пулов имён")
            val typeSize = b.getInt(start + typeStringsOff + 4)
            val keySize = b.getInt(start + keyStringsOff + 4)
            val chunks = ArrayList<Any>()
            var pos = start + maxOf(typeStringsOff + typeSize, keyStringsOff + keySize)
            val end = start + size
            while (pos + 8 <= end) {
                val type = b.getShort(pos).toInt() and 0xFFFF
                val chunkSize = b.getInt(pos + 4)
                if (chunkSize < 8 || pos + chunkSize > end) throw IOException("Повреждённый блок пакета $name")
                val raw = bytes.copyOfRange(pos, pos + chunkSize)
                chunks += when (type) {
                    ArscTable.RES_TABLE_TYPE_TYPE -> TypeChunk(raw)
                    ArscTable.RES_TABLE_TYPE_SPEC_TYPE -> SpecChunk(raw)
                    else -> raw
                }
                pos += chunkSize
            }
            return ArscPackage(
                bytes.copyOfRange(start, start + headerSize), id, name,
                StringPool.parse(bytes, start + typeStringsOff), StringPool.parse(bytes, start + keyStringsOff),
                bytes.copyOfRange(start + typeStringsOff, start + typeStringsOff + typeSize),
                bytes.copyOfRange(start + keyStringsOff, start + keyStringsOff + keySize),
                chunks,
            )
        }
    }
}

/** Квалификаторы ResTable_config в виде суффикса папки ресурсов (как aapt). */
object ConfigQualifiers {
    fun locale(c: ByteArray): String {
        fun u8(o: Int) = if (o < c.size) c[o].toInt() and 0xFF else 0
        val lang = when {
            u8(8) == 0 -> ""
            u8(8) and 0x80 != 0 -> {
                val l0 = u8(8); val l1 = u8(9)
                "" + ((l1 and 0x1F) + 'a'.code).toChar() + ((((l0 and 0x03) shl 3) or ((l1 and 0xE0) shr 5)) + 'a'.code).toChar() + (((l0 and 0x7C) shr 2) + 'a'.code).toChar()
            }
            else -> ("" + u8(8).toChar() + (if (u8(9) != 0) u8(9).toChar() else ' ')).trim()
        }
        if (lang.isEmpty()) return ""
        val region = when {
            u8(10) == 0 -> ""
            u8(10) and 0x80 != 0 -> {
                val r0 = u8(10); val r1 = u8(11)
                "" + ((r1 and 0x1F) + '0'.code).toChar() + ((((r1 and 0xE0) shr 5) or ((r0 and 0x03) shl 3)) + '0'.code).toChar() + (((r0 and 0x7C) shr 2) + '0'.code).toChar()
            }
            else -> ("" + u8(10).toChar() + (if (u8(11) != 0) u8(11).toChar() else ' ')).trim()
        }
        fun ascii(o: Int, n: Int) = (0 until n).map { u8(o + it) }.takeWhile { it != 0 }.map { it.toChar() }.joinToString("")
        val script = ascii(36, 4)
        val variant = ascii(40, 8)
        val computed = u8(52) != 0
        return if ((script.isEmpty() || computed) && variant.isEmpty()) {
            if (region.isEmpty()) lang else "$lang-r$region"
        } else {
            listOfNotNull("b", lang, script.takeIf { !computed && it.isNotEmpty() }, region.ifEmpty { null }, variant.ifEmpty { null }).joinToString("+")
        }
    }

    fun format(c: ByteArray): String {
        fun u8(o: Int) = if (o < c.size) c[o].toInt() and 0xFF else 0
        fun u16(o: Int) = u8(o) or (u8(o + 1) shl 8)
        val parts = ArrayList<String>()
        if (u16(4) != 0) parts += "mcc${u16(4)}"
        if (u16(6) != 0) parts += if (u16(6) == 0xFFFF) "mnc00" else "mnc${u16(6)}"
        locale(c).takeIf { it.isNotEmpty() }?.let { parts += it }
        val layout = u8(28)
        when (layout and 0xC0) { 0x40 -> parts += "ldltr"; 0x80 -> parts += "ldrtl" }
        if (u16(30) != 0) parts += "sw${u16(30)}dp"
        if (u16(32) != 0) parts += "w${u16(32)}dp"
        if (u16(34) != 0) parts += "h${u16(34)}dp"
        when (layout and 0x0F) { 1 -> parts += "small"; 2 -> parts += "normal"; 3 -> parts += "large"; 4 -> parts += "xlarge" }
        when (layout and 0x30) { 0x10 -> parts += "notlong"; 0x20 -> parts += "long" }
        when (u8(48) and 0x03) { 1 -> parts += "notround"; 2 -> parts += "round" }
        when (u8(49) and 0x03) { 1 -> parts += "nowidecg"; 2 -> parts += "widecg" }
        when (u8(49) and 0x0C) { 0x04 -> parts += "lowdr"; 0x08 -> parts += "highdr" }
        when (u8(12)) { 1 -> parts += "port"; 2 -> parts += "land"; 3 -> parts += "square" }
        val ui = u8(29)
        when (ui and 0x0F) { 2 -> parts += "desk"; 3 -> parts += "car"; 4 -> parts += "television"; 5 -> parts += "appliance"; 6 -> parts += "watch"; 7 -> parts += "vrheadset" }
        when (ui and 0x30) { 0x10 -> parts += "notnight"; 0x20 -> parts += "night" }
        when (val d = u16(14)) {
            0 -> Unit
            120 -> parts += "ldpi"; 160 -> parts += "mdpi"; 213 -> parts += "tvdpi"; 240 -> parts += "hdpi"
            320 -> parts += "xhdpi"; 480 -> parts += "xxhdpi"; 640 -> parts += "xxxhdpi"
            0xFFFE -> parts += "anydpi"; 0xFFFF -> parts += "nodpi"
            else -> parts += "${d}dpi"
        }
        when (u8(13)) { 1 -> parts += "notouch"; 2 -> parts += "stylus"; 3 -> parts += "finger" }
        when (u8(18) and 0x03) { 1 -> parts += "keysexposed"; 2 -> parts += "keyshidden"; 3 -> parts += "keyssoft" }
        when (u8(16)) { 1 -> parts += "nokeys"; 2 -> parts += "qwerty"; 3 -> parts += "12key" }
        when (u8(18) and 0x0C) { 0x04 -> parts += "navexposed"; 0x08 -> parts += "navhidden" }
        when (u8(17)) { 1 -> parts += "nonav"; 2 -> parts += "dpad"; 3 -> parts += "trackball"; 4 -> parts += "wheel" }
        if (u16(20) != 0 || u16(22) != 0) parts += "${u16(20)}x${u16(22)}"
        if (u16(24) != 0) parts += "v${u16(24)}"
        return parts.joinToString("-")
    }

    /** Язык в конфигурацию: `ru`, `pt-rBR`, `fil`, `b+sr+Latn`, `b+es+419`. */
    fun writeLocale(out: ByteArray, locale: String) {
        val (lang, region, script) = when {
            locale.startsWith("b+") -> {
                val p = locale.removePrefix("b+").split('+')
                val sc = p.drop(1).firstOrNull { it.length == 4 }
                val rg = p.drop(1).firstOrNull { it.length == 2 || (it.length == 3 && it.all(Char::isDigit)) }
                Triple(p[0], rg ?: "", sc ?: "")
            }
            "-r" in locale -> Triple(locale.substringBefore("-r"), locale.substringAfter("-r"), "")
            else -> Triple(locale, "", "")
        }
        require(lang.length in 2..3 && lang.all { it in 'a'..'z' }) { "Код языка: 2–3 строчные буквы (ru, en, fil)" }
        if (lang.length == 2) { out[8] = lang[0].code.toByte(); out[9] = lang[1].code.toByte() }
        else {
            val a = lang[0] - 'a'; val b = lang[1] - 'a'; val c = lang[2] - 'a'
            out[9] = ((b and 0x07) shl 5 or a).toByte()
            out[8] = (0x80 or (c shl 2) or (b shr 3)).toByte()
        }
        when {
            region.isEmpty() -> Unit
            region.length == 2 -> { out[10] = region[0].uppercaseChar().code.toByte(); out[11] = region[1].uppercaseChar().code.toByte() }
            region.length == 3 && region.all(Char::isDigit) -> {
                val a = region[0] - '0'; val b = region[1] - '0'; val c = region[2] - '0'
                out[11] = ((b and 0x07) shl 5 or a).toByte()
                out[10] = (0x80 or (c shl 2) or (b shr 3)).toByte()
            }
            else -> throw IllegalArgumentException("Регион: 2 буквы или 3 цифры")
        }
        if (script.isNotEmpty() && out.size >= 40) for (i in 0 until 4) out[36 + i] = script[i].code.toByte()
    }
}
