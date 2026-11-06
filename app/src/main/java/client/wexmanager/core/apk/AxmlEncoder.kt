package client.wexmanager.core.apk

import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Ошибка в тексте XML, понятная пользователю (с номером строки). */
class AxmlEncodeException(message: String) : IllegalArgumentException(message)

/**
 * Текстовый XML → бинарный AXML (как в APK). Типы значений берутся из исходного файла
 * ([hintsFrom]): правка `versionName="1"` остаётся строкой, а `versionCode="2"` — числом.
 * Для новых атрибутов тип угадывается по виду значения.
 */
object AxmlEncoder {
    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_XML_TYPE = 0x0003
    private const val RES_XML_START_NAMESPACE_TYPE = 0x0100
    private const val RES_XML_END_NAMESPACE_TYPE = 0x0101
    private const val RES_XML_START_ELEMENT_TYPE = 0x0102
    private const val RES_XML_END_ELEMENT_TYPE = 0x0103
    private const val RES_XML_CDATA_TYPE = 0x0104
    private const val RES_XML_RESOURCE_MAP_TYPE = 0x0180

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

    internal val DIMEN_UNITS = listOf("px", "dp", "sp", "pt", "in", "mm")
    internal val FRACTION_UNITS = listOf("%", "%p")

    /** Подсказки из исходного документа: типы значений и id ресурсов атрибутов. */
    class Hints(
        val types: Map<String, Int>,
        val typesByName: Map<String, Int>,
        val resIds: Map<String, Int>,
        /**
         * Точные подсказки: «элемент|android:name#номер» → типы атрибутов. Ключ не зависит от вставки
         * других элементов, поэтому у каждого meta-data android:value остаётся свой тип.
         */
        val keyed: Map<String, Map<String, Int>> = emptyMap(),
        /** Исходные значения атрибутов по тем же ключам: тип STRING окончателен, только если значение не меняли. */
        val keyedValues: Map<String, Map<String, String>> = emptyMap(),
    ) {
        companion object {
            val EMPTY = Hints(emptyMap(), emptyMap(), emptyMap())

            fun from(doc: AxmlDocument): Hints {
                val types = HashMap<String, Int>()
                val byName = HashMap<String, Int>()
                val ids = HashMap<String, Int>()
                val keyed = HashMap<String, Map<String, Int>>()
                val keyedValues = HashMap<String, Map<String, String>>()
                val seen = HashMap<String, Int>()
                fun walk(e: XmlElement) {
                    val base = elementKey(e.name, e.attrs.firstOrNull { it.prefix == "android" && it.name == "name" }?.value)
                    val n = seen.merge(base, 1, Int::plus)!!
                    keyed["$base#$n"] = e.attrs.filter { it.type >= 0 }.associate { it.qualifiedName to it.type }
                    keyedValues["$base#$n"] = e.attrs.associate { it.qualifiedName to it.value }
                    for (a in e.attrs) {
                        if (a.type >= 0) {
                            types.putIfAbsent("${e.name}/${a.qualifiedName}", a.type)
                            byName.putIfAbsent(a.qualifiedName, a.type)
                        }
                        if (a.resourceId != 0) ids.putIfAbsent(a.qualifiedName, a.resourceId)
                    }
                    e.children.forEach { if (it is XmlElement) walk(it) }
                }
                walk(doc.root)
                return Hints(types, byName, ids, keyed, keyedValues)
            }
        }
    }

    private fun elementKey(name: String, androidName: String?) = "$name|${androidName ?: ""}"

    private class Attr(val ns: String, val name: String, val prefixed: String, val value: String, val resId: Int, val line: Int)

    private sealed interface Node
    private class NsStart(val prefix: String, val uri: String, val line: Int) : Node
    private class NsEnd(val prefix: String, val uri: String, val line: Int) : Node
    private class Start(val ns: String, val name: String, val attrs: List<Attr>, val line: Int) : Node
    private class End(val ns: String, val name: String, val line: Int) : Node
    private class Text(val text: String, val line: Int) : Node

    /**
     * [parser] — XmlPullParser с поддержкой пространств имён (на устройстве — android.util.Xml),
     * [androidAttrId] — id атрибута android:name, если его нет в подсказках (android.R.attr).
     */
    fun encode(text: String, parser: XmlPullParser, hints: Hints, androidAttrId: (String) -> Int = { 0 }): ByteArray {
        val nodes = ArrayList<Node>()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(text))
        val nsStack = ArrayList<List<Pair<String, String>>>()
        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val depth = parser.depth
                        val declared = (parser.getNamespaceCount(depth - 1) until parser.getNamespaceCount(depth)).map { i ->
                            (parser.getNamespacePrefix(i) ?: "") to parser.getNamespaceUri(i)
                        }
                        declared.forEach { (p, u) -> nodes += NsStart(p, u, parser.lineNumber) }
                        nsStack += declared
                        val attrs = (0 until parser.attributeCount).map { i ->
                            val ns = parser.getAttributeNamespace(i) ?: ""
                            val name = parser.getAttributeName(i)
                            val prefix = parser.getAttributePrefix(i)
                            val prefixed = if (prefix.isNullOrEmpty()) name else "$prefix:$name"
                            val resId = if (ns == AxmlDecoder.ANDROID_NS) {
                                hints.resIds[prefixed] ?: hints.resIds["android:$name"] ?: androidAttrId(name)
                            } else hints.resIds[prefixed] ?: 0
                            if (ns == AxmlDecoder.ANDROID_NS && resId == 0) {
                                throw AxmlEncodeException("Строка ${parser.lineNumber}: неизвестный атрибут android:$name")
                            }
                            Attr(ns, name, prefixed, parser.getAttributeValue(i), resId, parser.lineNumber)
                        }
                        nodes += Start(parser.namespace ?: "", parser.name, attrs, parser.lineNumber)
                    }
                    XmlPullParser.END_TAG -> {
                        nodes += End(parser.namespace ?: "", parser.name, parser.lineNumber)
                        nsStack.removeLastOrNull()?.asReversed()?.forEach { (p, u) -> nodes += NsEnd(p, u, parser.lineNumber) }
                    }
                    XmlPullParser.TEXT -> if (parser.text.isNotBlank()) nodes += Text(parser.text, parser.lineNumber)
                }
                event = parser.next()
            }
        } catch (e: org.xmlpull.v1.XmlPullParserException) {
            throw AxmlEncodeException("Ошибка XML в строке ${e.lineNumber}: ${e.message?.substringBefore(" (position")}")
        }
        if (nodes.none { it is Start }) throw AxmlEncodeException("В документе нет элементов")

        // ---- Пул строк: сначала имена атрибутов с id ресурсов (параллельно карте ресурсов), затем остальные.
        val resNames = LinkedHashMap<Pair<String, Int>, Int>()
        nodes.filterIsInstance<Start>().flatMap { it.attrs }.filter { it.resId != 0 }
            .map { it.name to it.resId }.distinct().sortedBy { it.second }
            .forEach { resNames[it] = resNames.size }
        val strings = ArrayList<String>(resNames.keys.map { it.first })
        val plain = HashMap<String, Int>()
        fun idx(s: String): Int = plain.getOrPut(s) { strings.add(s); strings.size - 1 }
        fun nsIdx(uri: String): Int = if (uri.isEmpty()) -1 else idx(uri)

        val originalStrings: Set<String> = hints.keyedValues.values.flatMapTo(HashSet()) { it.values }

        fun typedValueAs(a: Attr, type: Int): Pair<Int, Int> {
            val v = a.value
            fun bad(what: String): Nothing = throw AxmlEncodeException("Строка ${a.line}: ${a.prefixed}=\"$v\" — ожидается $what")
            return when (type) {
                TYPE_STRING -> TYPE_STRING to idx(v)
                TYPE_NULL -> when {
                    v.isEmpty() -> TYPE_NULL to 1
                    v == "@null" -> TYPE_REFERENCE to 0
                    else -> TYPE_STRING to idx(v)
                }
                TYPE_REFERENCE, TYPE_DYNAMIC_REFERENCE -> when {
                    v == "@null" -> TYPE_REFERENCE to 0
                    v.startsWith("@0x") -> type to (v.substring(3).toLongOrNull(16)?.toInt() ?: bad("ссылка вида @0x7f010001"))
                    // Ссылку заменили строкой (например, подпись приложения текстом) — это допустимо.
                    !v.startsWith("@") -> TYPE_STRING to idx(v)
                    else -> bad("ссылка вида @0x7f010001 (имена ресурсов @string/… в APK не разрешаются)")
                }
                TYPE_ATTRIBUTE, TYPE_DYNAMIC_ATTRIBUTE ->
                    if (v.startsWith("?0x")) type to (v.substring(3).toLongOrNull(16)?.toInt() ?: bad("?0x…")) else bad("атрибут темы вида ?0x01010000")
                TYPE_INT_BOOLEAN -> when (v) {
                    "true" -> TYPE_INT_BOOLEAN to -1
                    "false" -> TYPE_INT_BOOLEAN to 0
                    else -> bad("true или false")
                }
                TYPE_INT_DEC, TYPE_INT_HEX -> (parseInt(v) ?: bad("целое число")).let { (if (HEX.matches(v)) TYPE_INT_HEX else type) to it }
                in TYPE_INT_COLOR_ARGB8..TYPE_INT_COLOR_RGB4 -> parseColor(v) ?: bad("цвет #AARRGGBB")
                TYPE_FLOAT -> TYPE_FLOAT to (v.toFloatOrNull()?.toRawBits() ?: bad("дробное число"))
                TYPE_DIMENSION -> TYPE_DIMENSION to (complex(v, DIMEN_UNITS) ?: bad("размер, например 16dp"))
                TYPE_FRACTION -> TYPE_FRACTION to (complex(v, FRACTION_UNITS) ?: bad("доля, например 50%"))
                else -> typedValueAs(a, guess(v))
            }
        }

        /** (тип, данные) значения атрибута; тип — из исходного файла, иначе по виду значения. */
        fun typedValue(a: Attr, hinted: Int?): Pair<Int, Int> {
            // Имена ресурсов (@style/…, ?attr/…) в готовом APK не разрешить — молча записать строку было бы хуже.
            if (RES_NAME.matches(a.value) && !(hinted == TYPE_STRING && a.value in originalStrings)) {
                throw AxmlEncodeException(
                    "Строка ${a.line}: ${a.prefixed}=\"${a.value}\" — имена ресурсов не разрешаются, укажите id: @0x7f… или ?0x…",
                )
            }
            // Значение не подходит под исходный тип (например, число заменили на true) — тип по виду значения.
            if (hinted != null && hinted != guess(a.value) && hinted != TYPE_STRING && hinted != TYPE_NULL) {
                runCatching { return typedValueAs(a, hinted) }
                // Было число/булево, стало не разбираемое значение — скорее опечатка, чем осознанная строка.
                if (guess(a.value) == TYPE_STRING && hinted != TYPE_REFERENCE && hinted != TYPE_DYNAMIC_REFERENCE) {
                    return typedValueAs(a, hinted) // бросит понятную ошибку «ожидается …»
                }
                return typedValueAs(a, guess(a.value))
            }
            return typedValueAs(a, hinted ?: guess(a.value))
        }

        val body = ByteArrayOutputStream()
        fun chunk(type: Int, headerSize: Int, write: (ByteBuffer) -> Unit, payloadSize: Int) {
            val size = headerSize + payloadSize
            val b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
            b.putShort(type.toShort()); b.putShort(headerSize.toShort()); b.putInt(size)
            write(b)
            body.write(b.array())
        }
        val seenKeys = HashMap<String, Int>()
        for (n in nodes) when (n) {
            is NsStart, is NsEnd -> {
                val (prefix, uri, line, type) = when (n) {
                    is NsStart -> listOf(n.prefix, n.uri, n.line, RES_XML_START_NAMESPACE_TYPE)
                    else -> (n as NsEnd).let { listOf(it.prefix, it.uri, it.line, RES_XML_END_NAMESPACE_TYPE) }
                }
                val p = idx(prefix as String)
                val u = idx(uri as String)
                chunk(type as Int, 16, { b -> b.putInt(line as Int); b.putInt(-1); b.putInt(p); b.putInt(u) }, 8)
            }
            is Start -> {
                // Тот же элемент исходника (по имени, android:name и номеру среди таких же) — его типы точные.
                val base = elementKey(n.name, n.attrs.firstOrNull { it.ns == AxmlDecoder.ANDROID_NS && it.name == "name" }?.value)
                val key = "$base#${seenKeys.merge(base, 1, Int::plus)}"
                val same = hints.keyed[key]
                val sameValues = hints.keyedValues[key]
                // Атрибуты по возрастанию id ресурса: фреймворк ищет их двоичным слиянием.
                val sorted = n.attrs.sortedWith(compareBy<Attr>({ if (it.resId == 0) 1 else 0 }, { it.resId.toLong() and 0xFFFFFFFFL }))
                val encoded = sorted.map { a ->
                    val nameIdx = if (a.resId != 0) resNames.getValue(a.name to a.resId) else idx(a.name)
                    // Неточные подсказки (по имени атрибута) не навязывают строку или ссылку — тип по виду значения.
                    val loose = (hints.types["${n.name}/${a.prefixed}"] ?: hints.typesByName[a.prefixed])
                        ?.takeUnless { it == TYPE_STRING || it == TYPE_NULL || it == TYPE_REFERENCE || it == TYPE_DYNAMIC_REFERENCE }
                    // Строка, которую изменили на ссылку (@0x…), — уже не строка.
                    val exact = same?.get(a.prefixed)?.takeUnless {
                        it == TYPE_STRING && sameValues?.get(a.prefixed) != a.value &&
                            guess(a.value).let { g -> g == TYPE_REFERENCE || g == TYPE_ATTRIBUTE }
                    }
                    val hinted = exact ?: loose
                    val (type, data) = typedValue(a, hinted)
                    val raw = if (type == TYPE_STRING) data else -1
                    intArrayOf(nsIdx(a.ns), nameIdx, raw, type, data)
                }
                fun special(name: String) = sorted.indexOfFirst { it.ns.isEmpty() && it.name == name } + 1
                // aapt2 отмечает android:id (0x010100d0), старый aapt — id без пространства имён.
                val idIndex = sorted.indexOfFirst { it.resId == 0x010100d0 }.let { if (it >= 0) it + 1 else special("id") }
                val elNs = nsIdx(n.ns)
                val elName = idx(n.name)
                chunk(RES_XML_START_ELEMENT_TYPE, 16, { b ->
                    b.putInt(n.line); b.putInt(-1)
                    b.putInt(elNs); b.putInt(elName)
                    b.putShort(20); b.putShort(20); b.putShort(encoded.size.toShort())
                    b.putShort(idIndex.toShort()); b.putShort(special("class").toShort()); b.putShort(special("style").toShort())
                    for (e in encoded) {
                        b.putInt(e[0]); b.putInt(e[1]); b.putInt(e[2])
                        b.putShort(8); b.put(0); b.put(e[3].toByte()); b.putInt(e[4])
                    }
                }, 20 + 20 * encoded.size) // ResXMLTree_attrExt (20) + атрибуты; строка/комментарий — в заголовке 16
            }
            is End -> {
                val elNs = nsIdx(n.ns)
                val elName = idx(n.name)
                chunk(RES_XML_END_ELEMENT_TYPE, 16, { b -> b.putInt(n.line); b.putInt(-1); b.putInt(elNs); b.putInt(elName) }, 8)
            }
            is Text -> {
                val t = idx(n.text)
                chunk(RES_XML_CDATA_TYPE, 16, { b ->
                    b.putInt(n.line); b.putInt(-1); b.putInt(t)
                    b.putShort(8); b.put(0); b.put(TYPE_STRING.toByte()); b.putInt(t)
                }, 12) // индекс строки (4) + Res_value (8)
            }
        }

        val pool = stringPool(strings)
        val resMap = ByteBuffer.allocate(8 + resNames.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(RES_XML_RESOURCE_MAP_TYPE.toShort()); putShort(8); putInt(8 + resNames.size * 4)
            resNames.keys.forEach { putInt(it.second) }
        }.array()
        val total = 8 + pool.size + resMap.size + body.size()
        val out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        out.putShort(RES_XML_TYPE.toShort()); out.putShort(8); out.putInt(total)
        out.put(pool); out.put(resMap); out.put(body.toByteArray())
        return out.array()
    }

    /** Пул строк в UTF-16 (так проще и читается любой версией Android). */
    private fun stringPool(strings: List<String>): ByteArray {
        val data = ByteArrayOutputStream()
        val offsets = IntArray(strings.size)
        for ((i, s) in strings.withIndex()) {
            offsets[i] = data.size()
            val len = s.length
            val b = ByteBuffer.allocate(4 + len * 2 + 2).order(ByteOrder.LITTLE_ENDIAN)
            if (len > 0x7FFF) {
                b.putShort(((len ushr 16) or 0x8000).toShort()); b.putShort((len and 0xFFFF).toShort())
            } else b.putShort(len.toShort())
            s.forEach { b.putChar(it) }
            b.putShort(0)
            data.write(b.array(), 0, b.position())
        }
        while (data.size() % 4 != 0) data.write(0)
        val header = 28
        val stringsStart = header + strings.size * 4
        val size = stringsStart + data.size()
        val b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        b.putShort(RES_STRING_POOL_TYPE.toShort()); b.putShort(header.toShort()); b.putInt(size)
        b.putInt(strings.size); b.putInt(0); b.putInt(0); b.putInt(stringsStart); b.putInt(0)
        offsets.forEach { b.putInt(it) }
        b.put(data.toByteArray())
        return b.array()
    }

    private val HEX = Regex("^0[xX][0-9a-fA-F]+$")
    /** @string/name, @android:style/X, ?attr/colorPrimary, @+id/x — имена, которые нужен aapt, чтобы разрешить. */
    private val RES_NAME = Regex("^[@?](\\+)?([a-zA-Z0-9_.]+:)?[a-zA-Z_][a-zA-Z0-9_]*/.+$")
    private val DEC = Regex("^-?\\d+$")
    private val COLOR = Regex("^#([0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")

    private val DIMEN = Regex("^-?\\d+(\\.\\d+)?(px|dp|sp|pt|in|mm)$")
    private val FRACTION = Regex("^-?\\d+(\\.\\d+)?%p?$")
    private val FLOAT = Regex("^-?\\d+\\.\\d+$")

    private fun guess(v: String): Int = when {
        v.startsWith("@0x") || v == "@null" -> TYPE_REFERENCE
        v.startsWith("?0x") -> TYPE_ATTRIBUTE
        v == "true" || v == "false" -> TYPE_INT_BOOLEAN
        DEC.matches(v) -> TYPE_INT_DEC
        HEX.matches(v) -> TYPE_INT_HEX
        COLOR.matches(v) -> TYPE_INT_COLOR_ARGB8
        DIMEN.matches(v) -> TYPE_DIMENSION
        FRACTION.matches(v) -> TYPE_FRACTION
        FLOAT.matches(v) -> TYPE_FLOAT
        else -> TYPE_STRING
    }

    private fun parseInt(v: String): Int? = when {
        HEX.matches(v) -> v.substring(2).toLongOrNull(16)?.toInt()
        DEC.matches(v) -> v.toLongOrNull()?.takeIf { it in Int.MIN_VALUE..0xFFFFFFFFL }?.toInt()
        else -> null
    }

    private fun parseColor(v: String): Pair<Int, Int>? {
        if (!COLOR.matches(v)) return null
        val h = v.substring(1)
        return when (h.length) {
            3 -> TYPE_INT_COLOR_RGB4 to (0xFF000000.toInt() or h.map { "$it$it" }.joinToString("").toInt(16))
            4 -> TYPE_INT_COLOR_ARGB4 to h.map { "$it$it" }.joinToString("").toLong(16).toInt()
            6 -> TYPE_INT_COLOR_RGB8 to (0xFF000000.toInt() or h.toInt(16))
            else -> TYPE_INT_COLOR_ARGB8 to h.toLong(16).toInt()
        }
    }

    /** Число с единицами → «complex» Android — как floatToComplex в AOSP (ResourceTypes.cpp). */
    internal fun complex(v: String, units: List<String>): Int? {
        val unit = units.withIndex().sortedByDescending { it.value.length }.firstOrNull { v.endsWith(it.value) } ?: return null
        val number = v.removeSuffix(unit.value).toFloatOrNull() ?: return null
        var f = if (units === FRACTION_UNITS) number / 100f else number
        val neg = f < 0
        if (neg) f = -f
        val bits = (f.toDouble() * (1 shl 23) + 0.5).toLong()
        val (radix, shift) = when {
            bits and 0x7fffffL == 0L -> 0 to 23 // 23p0 — целое
            bits and 0x7fffffL.inv() == 0L -> 3 to 0 // 0p23
            bits and 0x7fffffffL.inv() == 0L -> 2 to 8 // 8p15
            bits and 0x7fffffffffL.inv() == 0L -> 1 to 16 // 16p7
            else -> 0 to 23
        }
        var mantissa = ((bits ushr shift) and 0xffffffL).toInt()
        if (neg) mantissa = (-mantissa) and 0xffffff
        return (mantissa shl 8) or (radix shl 4) or unit.index
    }
}
