package client.wexmanager.core.apk

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Узел дерева, полученного из бинарного XML. */
sealed interface XmlNode

/** [type] — исходный тип значения (Res_value.dataType), нужен, чтобы закодировать правки обратно тем же типом. */
class XmlAttr(val prefix: String?, val name: String, val value: String, val resourceId: Int, val type: Int = -1) {
    val qualifiedName: String get() = if (prefix.isNullOrEmpty()) name else "$prefix:$name"
}

class XmlText(val text: String) : XmlNode

class XmlElement(
    val prefix: String?,
    val name: String,
    val attrs: List<XmlAttr>,
    /** Объявления xmlns, появившиеся перед этим элементом: prefix → uri. */
    val namespaces: List<Pair<String, String>>,
) : XmlNode {
    val children = ArrayList<XmlNode>()
    val qualifiedName: String get() = if (prefix.isNullOrEmpty()) name else "$prefix:$name"

    fun attr(name: String): String? = attrs.firstOrNull { it.name == name }?.value
    fun elements(name: String): List<XmlElement> = children.filterIsInstance<XmlElement>().filter { it.name == name }

    /** Все потомки с данным именем (обход в глубину). */
    fun findAll(name: String): List<XmlElement> {
        val out = ArrayList<XmlElement>()
        fun walk(e: XmlElement) {
            e.children.forEach { c ->
                if (c is XmlElement) {
                    if (c.name == name) out += c
                    walk(c)
                }
            }
        }
        walk(this)
        return out
    }
}

class AxmlDocument(val root: XmlElement) {

    /** Текстовое представление с отступами (стиль Android Studio: атрибуты столбиком). */
    fun toXml(indent: String = "    "): String {
        val sb = StringBuilder(8192)
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        val needAndroidNs = root.namespaces.none { it.second == AxmlDecoder.ANDROID_NS } && usesAndroidPrefix(root)
        writeElement(sb, root, 0, indent, if (needAndroidNs) listOf("android" to AxmlDecoder.ANDROID_NS) else emptyList())
        return sb.toString()
    }

    private fun usesAndroidPrefix(e: XmlElement): Boolean =
        e.attrs.any { it.prefix == "android" } || e.children.any { it is XmlElement && usesAndroidPrefix(it) }

    private fun writeElement(sb: StringBuilder, e: XmlElement, depth: Int, indent: String, extraNs: List<Pair<String, String>>) {
        val pad = indent.repeat(depth)
        sb.append(pad).append('<').append(e.qualifiedName)
        val attrLines = ArrayList<String>()
        (extraNs + e.namespaces).forEach { (p, uri) ->
            attrLines += (if (p.isEmpty()) "xmlns" else "xmlns:$p") + "=\"" + escape(uri) + "\""
        }
        e.attrs.forEach { a -> attrLines += a.qualifiedName + "=\"" + escape(a.value) + "\"" }
        if (attrLines.size == 1) {
            sb.append(' ').append(attrLines[0])
        } else if (attrLines.size > 1) {
            val attrPad = pad + indent
            attrLines.forEachIndexed { i, line ->
                if (i == 0) sb.append(' ').append(line) else sb.append('\n').append(attrPad).append(line)
            }
        }
        if (e.children.isEmpty()) {
            sb.append(" />\n")
            return
        }
        sb.append(">\n")
        e.children.forEach { c ->
            when (c) {
                is XmlElement -> writeElement(sb, c, depth + 1, indent, emptyList())
                is XmlText -> {
                    val t = c.text.trim()
                    if (t.isNotEmpty()) sb.append(pad).append(indent).append(escape(t, attr = false)).append('\n')
                }
            }
        }
        sb.append(pad).append("</").append(e.qualifiedName).append(">\n")
    }

    private fun escape(s: String, attr: Boolean = true): String {
        if (s.none { it == '&' || it == '<' || it == '>' || it == '"' || it < ' ' }) return s
        val sb = StringBuilder(s.length + 16)
        for (ch in s) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' && attr -> sb.append("&quot;")
                ch == '\n' -> sb.append(if (attr) "&#10;" else "\n")
                ch == '\t' -> sb.append(if (attr) "&#9;" else "\t")
                ch == '\r' -> sb.append("&#13;")
                ch < ' ' -> sb.append("&#").append(ch.code).append(';')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}

/**
 * Декодер бинарного Android XML (AXML), как в AndroidManifest.xml внутри APK.
 * Устойчив к типичным приёмам обфускации: пустые имена атрибутов восстанавливаются по resourceId.
 */
object AxmlDecoder {
    const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_XML_TYPE = 0x0003
    private const val RES_XML_START_NAMESPACE_TYPE = 0x0100
    private const val RES_XML_END_NAMESPACE_TYPE = 0x0101
    private const val RES_XML_START_ELEMENT_TYPE = 0x0102
    private const val RES_XML_END_ELEMENT_TYPE = 0x0103
    private const val RES_XML_CDATA_TYPE = 0x0104
    private const val RES_XML_RESOURCE_MAP_TYPE = 0x0180

    private const val UTF8_FLAG = 1 shl 8

    private const val TYPE_NULL = 0x00
    private const val TYPE_REFERENCE = 0x01
    private const val TYPE_ATTRIBUTE = 0x02
    private const val TYPE_STRING = 0x03
    private const val TYPE_FLOAT = 0x04
    private const val TYPE_DIMENSION = 0x05
    private const val TYPE_FRACTION = 0x06
    private const val TYPE_DYNAMIC_REFERENCE = 0x07
    private const val TYPE_DYNAMIC_ATTRIBUTE = 0x08
    private const val TYPE_INT_DEC = 0x10
    private const val TYPE_INT_HEX = 0x11
    private const val TYPE_INT_BOOLEAN = 0x12
    private const val TYPE_FIRST_COLOR = 0x1c
    private const val TYPE_LAST_COLOR = 0x1f

    private val RADIX_MULTS = floatArrayOf(1f / (1 shl 8), 1f / (1 shl 15), 1f / (1 shl 23), 1f / (1L shl 31).toFloat())
    private val DIMEN_UNITS = arrayOf("px", "dp", "sp", "pt", "in", "mm")
    private val FRACTION_UNITS = arrayOf("%", "%p")

    /**
     * Имена атрибутов android:* по resourceId — используются, когда строка имени стёрта
     * или испорчена обфускатором (сам Android сопоставляет атрибуты именно по id).
     */
    private val ANDROID_ATTRS: Map<Int, String> = mapOf(
        0x01010000 to "theme",
        0x01010001 to "label",
        0x01010002 to "icon",
        0x01010003 to "name",
        0x01010004 to "manageSpaceActivity",
        0x01010005 to "allowClearUserData",
        0x01010006 to "permission",
        0x01010007 to "readPermission",
        0x01010008 to "writePermission",
        0x01010009 to "protectionLevel",
        0x0101000a to "permissionGroup",
        0x0101000b to "sharedUserId",
        0x0101000c to "hasCode",
        0x0101000d to "persistent",
        0x0101000e to "enabled",
        0x0101000f to "debuggable",
        0x01010010 to "exported",
        0x01010011 to "process",
        0x01010012 to "taskAffinity",
        0x01010013 to "multiprocess",
        0x01010014 to "finishOnTaskLaunch",
        0x01010015 to "clearTaskOnLaunch",
        0x01010016 to "stateNotNeeded",
        0x01010017 to "excludeFromRecents",
        0x01010018 to "authorities",
        0x01010019 to "syncable",
        0x0101001a to "initOrder",
        0x0101001b to "grantUriPermissions",
        0x0101001c to "priority",
        0x0101001d to "launchMode",
        0x0101001e to "screenOrientation",
        0x0101001f to "configChanges",
        0x01010020 to "description",
        0x01010021 to "targetPackage",
        0x01010022 to "handleProfiling",
        0x01010023 to "functionalTest",
        0x01010024 to "value",
        0x01010025 to "resource",
        0x01010026 to "mimeType",
        0x01010027 to "scheme",
        0x01010028 to "host",
        0x01010029 to "port",
        0x0101002a to "path",
        0x0101002b to "pathPrefix",
        0x0101002c to "pathPattern",
        0x0101002d to "action",
        0x0101002e to "data",
        0x0101002f to "targetClass",
        0x01010202 to "targetActivity",
        0x0101020c to "minSdkVersion",
        0x0101021b to "versionCode",
        0x0101021c to "versionName",
        0x0101022b to "windowSoftInputMode",
        0x01010270 to "targetSdkVersion",
        0x01010271 to "maxSdkVersion",
        0x01010272 to "testOnly",
        0x01010280 to "allowBackup",
        0x01010281 to "glEsVersion",
        0x0101028e to "required",
        0x010102b7 to "installLocation",
        0x010102d3 to "hardwareAccelerated",
        0x0101035a to "largeHeap",
        0x010103af to "supportsRtl",
        0x010103f2 to "banner",
        0x010104ea to "extractNativeLibs",
        0x010104eb to "fullBackupContent",
        0x01010505 to "directBootAware",
        0x0101052c to "roundIcon",
        0x01010572 to "compileSdkVersion",
        0x01010573 to "compileSdkVersionCodename",
        0x0101057a to "appComponentFactory",
        0x01010599 to "foregroundServiceType",
        0x01010603 to "requestLegacyExternalStorage",
        0x0101063e to "dataExtractionRules",
        0x0101066c to "enableOnBackInvokedCallback",
    )

    const val ATTR_MIN_SDK = 0x0101020c
    const val ATTR_TARGET_SDK = 0x01010270
    const val ATTR_COMPILE_SDK = 0x01010572

    private val identifier = Regex("[A-Za-z_][A-Za-z0-9_.\\-]*")

    fun decode(bytes: ByteArray): AxmlDocument {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bytes.size >= 8) { "Файл слишком короткий" }
        val fileType = buf.getShort(0).toInt() and 0xFFFF
        require(fileType == RES_XML_TYPE) { "Не бинарный XML (тип чанка 0x%04x)".format(fileType) }
        val fileHeaderSize = (buf.getShort(2).toInt() and 0xFFFF).coerceAtLeast(8)
        val fileSize = buf.getInt(4).let { if (it <= 0 || it > bytes.size) bytes.size else it }

        var strings: Array<String> = emptyArray()
        var resMap = IntArray(0)
        // uri → стек префиксов (с учётом вложенных объявлений)
        val uriToPrefix = HashMap<String, ArrayDeque<String>>()
        val pendingNs = ArrayList<Pair<String, String>>()
        val stack = ArrayDeque<XmlElement>()
        var root: XmlElement? = null

        fun str(idx: Int): String? = if (idx < 0 || idx >= strings.size) null else strings[idx]
        fun prefixFor(uriIdx: Int): String? {
            val uri = str(uriIdx) ?: return null
            if (uri.isEmpty()) return null
            return uriToPrefix[uri]?.lastOrNull() ?: if (uri == ANDROID_NS) "android" else null
        }

        var pos = fileHeaderSize
        while (pos + 8 <= fileSize) {
            val type = buf.getShort(pos).toInt() and 0xFFFF
            val headerSize = buf.getShort(pos + 2).toInt() and 0xFFFF
            val size = buf.getInt(pos + 4)
            if (size < 8 || pos + size > bytes.size) break
            val ext = pos + headerSize
            when (type) {
                RES_STRING_POOL_TYPE -> strings = readStringPool(buf, pos, size)
                RES_XML_RESOURCE_MAP_TYPE -> {
                    val count = (size - headerSize) / 4
                    resMap = IntArray(count.coerceAtLeast(0)) { buf.getInt(ext + it * 4) }
                }
                RES_XML_START_NAMESPACE_TYPE -> {
                    val prefix = str(buf.getInt(ext)) ?: ""
                    val uri = str(buf.getInt(ext + 4)) ?: ""
                    uriToPrefix.getOrPut(uri) { ArrayDeque() }.addLast(prefix)
                    pendingNs += prefix to uri
                }
                RES_XML_END_NAMESPACE_TYPE -> {
                    val uri = str(buf.getInt(ext + 4)) ?: ""
                    uriToPrefix[uri]?.removeLastOrNull()
                }
                RES_XML_START_ELEMENT_TYPE -> {
                    val nsIdx = buf.getInt(ext)
                    val nameIdx = buf.getInt(ext + 4)
                    val attrStart = buf.getShort(ext + 8).toInt() and 0xFFFF
                    val attrSize = (buf.getShort(ext + 10).toInt() and 0xFFFF).let { if (it < 20) 20 else it }
                    val attrCount = buf.getShort(ext + 12).toInt() and 0xFFFF
                    val attrs = ArrayList<XmlAttr>(attrCount)
                    for (i in 0 until attrCount) {
                        val a = ext + attrStart + i * attrSize
                        if (a + 20 > pos + size) break
                        val aNs = buf.getInt(a)
                        val aName = buf.getInt(a + 4)
                        val aRaw = buf.getInt(a + 8)
                        val dataType = buf.get(a + 15).toInt() and 0xFF
                        val data = buf.getInt(a + 16)
                        val resId = if (aName >= 0 && aName < resMap.size) resMap[aName] else 0
                        val tableName = ANDROID_ATTRS[resId]
                        val rawName = str(aName).orEmpty()
                        val name = when {
                            // Имя по id фреймворка надёжнее строки из пула: в обфусцированных APK строки бывают подменены.
                            tableName != null -> tableName
                            identifier.matches(rawName) -> rawName
                            resId != 0 -> "attr_0x%08x".format(resId)
                            else -> "attr$i"
                        }
                        var prefix = prefixFor(aNs)
                        if (prefix == null && (resId ushr 24) == 0x01) prefix = "android"
                        val value = formatValue(dataType, data, aRaw, ::str)
                        attrs += XmlAttr(prefix, name, value, resId, dataType)
                    }
                    val el = XmlElement(prefixFor(nsIdx), str(nameIdx) ?: "unknown", attrs, pendingNs.toList())
                    pendingNs.clear()
                    val parent = stack.lastOrNull()
                    if (parent != null) parent.children += el else if (root == null) root = el
                    stack.addLast(el)
                }
                RES_XML_END_ELEMENT_TYPE -> {
                    stack.removeLastOrNull()
                }
                RES_XML_CDATA_TYPE -> {
                    val text = str(buf.getInt(ext))
                    if (text != null) stack.lastOrNull()?.children?.add(XmlText(text))
                }
            }
            pos += size
        }
        return AxmlDocument(root ?: throw IllegalArgumentException("В документе нет элементов"))
    }

    private fun readStringPool(buf: ByteBuffer, start: Int, chunkSize: Int): Array<String> {
        val headerSize = buf.getShort(start + 2).toInt() and 0xFFFF
        val count = buf.getInt(start + 8)
        val flags = buf.getInt(start + 16)
        val stringsStart = buf.getInt(start + 20)
        val utf8 = flags and UTF8_FLAG != 0
        val end = start + chunkSize
        if (count <= 0 || count > 1_000_000) return emptyArray()
        val offsetsAt = start + headerSize
        val dataAt = start + stringsStart
        return Array(count) { i ->
            runCatching {
                val off = buf.getInt(offsetsAt + i * 4)
                val p = dataAt + off
                if (p < 0 || p >= end) "" else if (utf8) readUtf8(buf, p, end) else readUtf16(buf, p, end)
            }.getOrDefault("")
        }
    }

    private fun readUtf8(buf: ByteBuffer, start: Int, end: Int): String {
        var p = start
        // Длина в UTF-16 символах (не используется), затем длина в байтах.
        val b0 = buf.get(p++).toInt() and 0xFF
        if (b0 and 0x80 != 0) p++
        var len = buf.get(p++).toInt() and 0xFF
        if (len and 0x80 != 0) len = ((len and 0x7F) shl 8) or (buf.get(p++).toInt() and 0xFF)
        if (p + len > end) len = (end - p).coerceAtLeast(0)
        val arr = ByteArray(len)
        for (i in 0 until len) arr[i] = buf.get(p + i)
        return String(arr, Charsets.UTF_8)
    }

    private fun readUtf16(buf: ByteBuffer, start: Int, end: Int): String {
        var p = start
        var len = buf.getShort(p).toInt() and 0xFFFF
        p += 2
        if (len and 0x8000 != 0) {
            len = ((len and 0x7FFF) shl 16) or (buf.getShort(p).toInt() and 0xFFFF)
            p += 2
        }
        if (p + len * 2 > end) len = ((end - p) / 2).coerceAtLeast(0)
        val chars = CharArray(len)
        for (i in 0 until len) chars[i] = buf.getChar(p + i * 2)
        return String(chars)
    }

    private fun formatValue(type: Int, data: Int, raw: Int, str: (Int) -> String?): String = when (type) {
        TYPE_STRING -> str(data) ?: str(raw) ?: ""
        TYPE_NULL -> if (data == 1) "" else (str(raw) ?: "@null")
        TYPE_REFERENCE, TYPE_DYNAMIC_REFERENCE -> if (data == 0) "@null" else "@0x%08x".format(data)
        TYPE_ATTRIBUTE, TYPE_DYNAMIC_ATTRIBUTE -> "?0x%08x".format(data)
        TYPE_FLOAT -> Float.fromBits(data).toString()
        TYPE_DIMENSION -> complexToFloat(data).fmt() + (DIMEN_UNITS.getOrNull(data and 0xF) ?: "")
        TYPE_FRACTION -> (complexToFloat(data) * 100f).fmt() + (FRACTION_UNITS.getOrNull(data and 0xF) ?: "")
        TYPE_INT_DEC -> data.toString()
        TYPE_INT_HEX -> "0x%x".format(data)
        TYPE_INT_BOOLEAN -> if (data != 0) "true" else "false"
        in TYPE_FIRST_COLOR..TYPE_LAST_COLOR -> "#%08X".format(data)
        else -> str(raw) ?: "0x%08x".format(data)
    }

    private fun complexToFloat(complex: Int): Float =
        (complex and (0xFFFFFF shl 8)).toFloat() * RADIX_MULTS[(complex shr 4) and 0x3]

    private fun Float.fmt(): String =
        if (this == kotlin.math.floor(this) && kotlin.math.abs(this) < 1e7f) toInt().toString() else toString()
}
