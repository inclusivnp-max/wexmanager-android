package client.wexmanager.core.elf

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/** Сегмент программы (PT_LOAD и др.). */
data class ElfSegment(val type: String, val offset: Long, val vaddr: Long, val fileSize: Long, val memSize: Long, val flags: String, val align: Long)

data class ElfSection(val name: String, val type: String, val offset: Long, val size: Long, val flags: String)

data class ElfSymbol(val name: String, val type: String, val bind: String, val value: Long, val size: Long, val defined: Boolean)

/** Разобранный ELF: заголовок, сегменты, секции, зависимости и динамические символы. */
data class ElfInfo(
    val is64: Boolean,
    val littleEndian: Boolean,
    val type: String,
    val machine: String,
    val entry: Long,
    val interpreter: String?,
    val soname: String?,
    val needed: List<String>,
    val runpath: String?,
    val buildId: String?,
    val segments: List<ElfSegment>,
    val sections: List<ElfSection>,
    val symbols: List<ElfSymbol>,
    val stripped: Boolean,
) {
    /** Наименьшее выравнивание PT_LOAD: < 16 КБ — не загрузится на устройствах со страницами 16 КБ (Android 15+). */
    val loadAlignment: Long? get() = segments.filter { it.type == "LOAD" }.minOfOrNull { it.align }
    val jniFunctions: List<ElfSymbol> get() = symbols.filter { it.defined && (it.name.startsWith("Java_") || it.name == "JNI_OnLoad" || it.name == "JNI_OnUnload") }
}

/** Разбор ELF (32/64 бит, LE/BE) без внешних библиотек: только чтение нужных таблиц через mmap. */
object Elf {
    private const val MAX_SYMBOLS = 200_000

    /**
     * Наименьшее выравнивание PT_LOAD по началу файла (заголовки программы лежат в первых килобайтах):
     * для проверки библиотек в APK без распаковки. null — не ELF или заголовки не поместились в [head].
     */
    fun loadAlignment(head: ByteArray): Long? {
        if (head.size < 64 || head[0] != 0x7F.toByte() || head[1] != 'E'.code.toByte() || head[2] != 'L'.code.toByte() || head[3] != 'F'.code.toByte()) return null
        val is64 = head[4].toInt() == 2
        val b = ByteBuffer.wrap(head).order(if (head[5].toInt() != 2) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
        val phoff = if (is64) b.getLong(32) else b.getInt(28).toLong() and 0xFFFFFFFFL
        val entSize = b.getShort(if (is64) 54 else 42).toInt() and 0xFFFF
        val count = b.getShort(if (is64) 56 else 44).toInt() and 0xFFFF
        if (entSize < (if (is64) 56 else 32) || phoff < 0) return null
        var min: Long? = null
        for (i in 0 until count) {
            val p = phoff + i.toLong() * entSize
            if (p < 0 || p > head.size - entSize) return min
            val q = p.toInt()
            if (b.getInt(q) != 1) continue // PT_LOAD
            val align = if (is64) b.getLong(q + 48) else b.getInt(q + 28).toLong() and 0xFFFFFFFFL
            min = if (min == null) align else minOf(min, align)
        }
        return min
    }

    fun isElf(file: File): Boolean = runCatching {
        file.inputStream().use { s -> val h = ByteArray(4); s.read(h) == 4 && h[0] == 0x7F.toByte() && h[1] == 'E'.code.toByte() && h[2] == 'L'.code.toByte() && h[3] == 'F'.code.toByte() }
    }.getOrDefault(false)

    fun parse(file: File): ElfInfo = RandomAccessFile(file, "r").use { raf ->
        val ch = raf.channel
        val size = ch.size()
        if (size < 52) throw IOException("Слишком короткий файл для ELF")
        val b = ch.map(FileChannel.MapMode.READ_ONLY, 0, size)
        if (b.get(0) != 0x7F.toByte() || b.get(1) != 'E'.code.toByte() || b.get(2) != 'L'.code.toByte() || b.get(3) != 'F'.code.toByte()) throw IOException("Не ELF-файл")
        val is64 = b.get(4).toInt() == 2
        val le = b.get(5).toInt() != 2
        b.order(if (le) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
        fun addr(p: Int): Long = if (is64) b.getLong(p) else b.getInt(p).toLong() and 0xFFFFFFFFL
        fun u16(p: Int) = b.getShort(p).toInt() and 0xFFFF
        fun u32(p: Int) = b.getInt(p).toLong() and 0xFFFFFFFFL
        // Без сложения: off + len переполнился бы на «смещениях» около Long.MAX из битого файла.
        fun inFile(off: Long, len: Long) = off >= 0 && len >= 0 && len <= size && off <= size - len

        val type = when (u16(16)) { 1 -> "REL (объектный)"; 2 -> "EXEC (исполняемый)"; 3 -> "DYN (библиотека / PIE)"; 4 -> "CORE"; else -> "0x" + u16(16).toString(16) }
        val machine = when (u16(18)) {
            3 -> "x86"; 8 -> "MIPS"; 40 -> "ARM"; 62 -> "x86_64"; 183 -> "AArch64 (arm64)"; 243 -> "RISC-V"; else -> "0x" + u16(18).toString(16)
        }
        val entry = addr(24)
        val phoff = addr(if (is64) 32 else 28)
        val shoff = addr(if (is64) 40 else 32)
        val phentsize = u16(if (is64) 54 else 42)
        val phnum = u16(if (is64) 56 else 44)
        val shentsize = u16(if (is64) 58 else 46)
        val shnum = u16(if (is64) 60 else 48)
        val shstrndx = u16(if (is64) 62 else 50)

        // Сегменты.
        val segments = ArrayList<ElfSegment>()
        var interp: String? = null
        var dynamicOff = -1L
        var dynamicSize = 0L
        // Записи меньше положенного — читать поля дальше их конца нельзя.
        val phOk = phentsize >= (if (is64) 56 else 32)
        val shOk = shentsize >= (if (is64) 64 else 40)
        for (i in 0 until if (phOk) phnum else 0) {
            val p = phoff + i.toLong() * phentsize
            if (!inFile(p, phentsize.toLong())) break
            val q = p.toInt()
            val ptype = u32(q)
            val (off, vaddr, filesz, memsz, flags, align) = if (is64) {
                listOf(b.getLong(q + 8), b.getLong(q + 16), b.getLong(q + 32), b.getLong(q + 40), u32(q + 4), b.getLong(q + 48))
            } else {
                listOf(u32(q + 4), u32(q + 8), u32(q + 16), u32(q + 20), u32(q + 24), u32(q + 28))
            }
            val name = when (ptype) {
                1L -> "LOAD"; 2L -> "DYNAMIC"; 3L -> "INTERP"; 4L -> "NOTE"; 6L -> "PHDR"; 7L -> "TLS"
                0x6474e550L -> "GNU_EH_FRAME"; 0x6474e551L -> "GNU_STACK"; 0x6474e552L -> "GNU_RELRO"; 0x6474e553L -> "GNU_PROPERTY"
                else -> "0x" + ptype.toString(16)
            }
            val f = buildString { append(if (flags and 4L != 0L) 'R' else '-'); append(if (flags and 2L != 0L) 'W' else '-'); append(if (flags and 1L != 0L) 'X' else '-') }
            segments += ElfSegment(name, off, vaddr, filesz, memsz, f, align)
            if (ptype == 3L && inFile(off, filesz)) interp = cString(b, off.toInt(), filesz.toInt())
            if (ptype == 2L) { dynamicOff = off; dynamicSize = filesz }
        }

        // Секции и имена секций.
        val rawSections = ArrayList<LongArray>() // name, type, flags, addr, offset, size, link, entsize
        for (i in 0 until if (shOk) shnum else 0) {
            val p = shoff + i.toLong() * shentsize
            if (shoff == 0L || !inFile(p, shentsize.toLong())) break
            val q = p.toInt()
            rawSections += if (is64) longArrayOf(u32(q), u32(q + 4), b.getLong(q + 8), b.getLong(q + 16), b.getLong(q + 24), b.getLong(q + 32), u32(q + 40), b.getLong(q + 56))
            else longArrayOf(u32(q), u32(q + 4), u32(q + 8), u32(q + 12), u32(q + 16), u32(q + 20), u32(q + 24), u32(q + 36))
        }
        val shstr = rawSections.getOrNull(shstrndx)
        fun secName(off: Long): String = if (shstr != null && inFile(shstr[4] + off, 1)) cString(b, (shstr[4] + off).toInt(), (shstr[5] - off).toInt().coerceAtLeast(0)) else ""
        val sections = rawSections.drop(1).map { s ->
            val t = when (s[1]) {
                1L -> "PROGBITS"; 2L -> "SYMTAB"; 3L -> "STRTAB"; 4L -> "RELA"; 5L -> "HASH"; 6L -> "DYNAMIC"; 7L -> "NOTE"; 8L -> "NOBITS"
                9L -> "REL"; 11L -> "DYNSYM"; 14L -> "INIT_ARRAY"; 15L -> "FINI_ARRAY"; 0x6ffffff6L -> "GNU_HASH"; 0x6fffffffL -> "VERSYM"; 0x6ffffffeL -> "VERNEED"
                else -> "0x" + s[1].toString(16)
            }
            val f = buildString { if (s[2] and 1L != 0L) append('W'); if (s[2] and 2L != 0L) append('A'); if (s[2] and 4L != 0L) append('X') }
            ElfSection(secName(s[0]), t, s[4], s[5], f)
        }

        // Динамическая секция: DT_NEEDED, DT_SONAME, DT_RUNPATH (строки — из .dynstr по адресу DT_STRTAB).
        val needed = ArrayList<String>()
        var soname: String? = null
        var runpath: String? = null
        val dynstr = rawSections.firstOrNull { s -> secName(s[0]) == ".dynstr" }
        if (dynamicOff >= 0 && dynstr != null) {
            val entSize = if (is64) 16 else 8
            var p = dynamicOff
            val nameOffsets = ArrayList<Pair<Long, Long>>()
            while (p + entSize <= dynamicOff + dynamicSize && inFile(p, entSize.toLong())) {
                val tag = if (is64) b.getLong(p.toInt()) else u32(p.toInt())
                val v = if (is64) b.getLong(p.toInt() + 8) else u32(p.toInt() + 4)
                if (tag == 0L) break
                if (tag == 1L || tag == 14L || tag == 29L || tag == 15L) nameOffsets += tag to v
                p += entSize
            }
            for ((tag, off) in nameOffsets) {
                if (!inFile(dynstr[4] + off, 1)) continue
                val s = cString(b, (dynstr[4] + off).toInt(), (dynstr[5] - off).toInt().coerceAtLeast(0))
                when (tag) { 1L -> needed += s; 14L -> soname = s; 29L, 15L -> runpath = s }
            }
        }

        // Символы: .dynsym (экспорт и импорт); .symtab — если не вычищен.
        val symbols = ArrayList<ElfSymbol>()
        fun readSymbols(tab: LongArray, strtab: LongArray) {
            val ent = if (tab[7] > 0) tab[7] else if (is64) 24L else 16L
            val count = (tab[5] / ent).coerceAtMost(MAX_SYMBOLS.toLong())
            for (i in 1 until count) {
                val p = tab[4] + i * ent
                if (!inFile(p, ent)) break
                val q = p.toInt()
                val nameOff = u32(q)
                val info: Int
                val shndx: Int
                val value: Long
                val sz: Long
                if (is64) { info = b.get(q + 4).toInt() and 0xFF; shndx = u16(q + 6); value = b.getLong(q + 8); sz = b.getLong(q + 16) }
                else { value = u32(q + 4); sz = u32(q + 8); info = b.get(q + 12).toInt() and 0xFF; shndx = u16(q + 14) }
                if (!inFile(strtab[4] + nameOff, 1)) continue
                val name = cString(b, (strtab[4] + nameOff).toInt(), (strtab[5] - nameOff).toInt().coerceAtLeast(0))
                if (name.isEmpty()) continue
                val stype = when (info and 0xF) { 0 -> "NOTYPE"; 1 -> "OBJECT"; 2 -> "FUNC"; 3 -> "SECTION"; 4 -> "FILE"; 6 -> "TLS"; 10 -> "IFUNC"; else -> "?" }
                if (stype == "SECTION" || stype == "FILE") continue
                val bind = when (info ushr 4) { 0 -> "LOCAL"; 1 -> "GLOBAL"; 2 -> "WEAK"; 10 -> "UNIQUE"; else -> "?" }
                symbols += ElfSymbol(name, stype, bind, value, sz, shndx != 0)
            }
        }
        val dynsym = rawSections.firstOrNull { it[1] == 11L }
        if (dynsym != null) rawSections.getOrNull(dynsym[6].toInt())?.let { readSymbols(dynsym, it) }
        val symtab = rawSections.firstOrNull { it[1] == 2L }
        if (symtab != null) rawSections.getOrNull(symtab[6].toInt())?.let { st ->
            val seen = symbols.mapTo(HashSet()) { it.name }
            val before = symbols.size
            readSymbols(symtab, st)
            // Из .symtab — только то, чего нет в .dynsym.
            val extra = symbols.subList(before, symbols.size).filter { it.name !in seen }
            while (symbols.size > before) symbols.removeAt(symbols.size - 1)
            symbols += extra
        }

        // Build ID из заметки NT_GNU_BUILD_ID.
        val buildId = sections.indices.firstNotNullOfOrNull { i ->
            val s = rawSections[i + 1]
            if (s[1] != 7L || !inFile(s[4], s[5])) return@firstNotNullOfOrNull null
            var p = s[4].toInt()
            val end = (s[4] + s[5]).toInt()
            var found: String? = null
            while (p + 12 <= end && found == null) {
                val nsz = b.getInt(p); val dsz = b.getInt(p + 4); val t = b.getInt(p + 8)
                val nameStart = p + 12
                val descStart = nameStart + ((nsz + 3) and 3.inv())
                if (nsz < 0 || dsz < 0 || descStart + dsz > end) break
                if (t == 3 && nsz == 4) found = (0 until dsz).joinToString("") { "%02x".format(b.get(descStart + it)) }
                p = descStart + ((dsz + 3) and 3.inv())
            }
            found
        }

        ElfInfo(
            is64, le, type, machine, entry, interp, soname, needed, runpath, buildId, segments, sections,
            symbols.sortedWith(compareBy<ElfSymbol>({ !it.defined }, { it.name })), stripped = symtab == null,
        )
    }

    private fun cString(b: ByteBuffer, at: Int, max: Int): String {
        var n = 0
        val limit = minOf(max, b.limit() - at, 4096)
        while (n < limit && b.get(at + n) != 0.toByte()) n++
        val bytes = ByteArray(n)
        for (i in 0 until n) bytes[i] = b.get(at + i)
        return String(bytes, Charsets.UTF_8)
    }

    private operator fun <T> List<T>.component6(): T = this[5]
}
