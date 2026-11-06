package client.wexmanager.core.network

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Общая папка SMB-сервера. */
data class SmbShareInfo(val name: String, val remark: String, val type: Int) {
    /** Обычная дисковая шара (не принтер, не IPC$, не скрытая административная). */
    val isDisk: Boolean get() = (type and 0x0FFFFFFF) == STYPE_DISKTREE && (type and STYPE_SPECIAL) == 0

    companion object {
        const val STYPE_DISKTREE = 0
        const val STYPE_SPECIAL = 0x80000000.toInt()
    }
}

/**
 * Список шар через DCE/RPC: канал `\PIPE\srvsvc`, вызов NetrShareEnum (opnum 15), уровень 1.
 * Минимальная реализация без сторонних RPC-библиотек: bind, request и разбор NDR-ответа.
 */
object SmbShares {
    private const val PTYPE_REQUEST = 0
    private const val PTYPE_RESPONSE = 2
    private const val PTYPE_FAULT = 3
    private const val PTYPE_BIND = 11
    private const val PTYPE_BIND_ACK = 12
    private const val FLAG_FIRST = 0x01
    private const val FLAG_LAST = 0x02
    private const val OPNUM_NETR_SHARE_ENUM = 15

    // srvsvc 4b324fc8-1670-01d3-1278-5a47bf6ee188 v3.0 и NDR 8a885d04-1ceb-11c9-9fe8-08002b104860 v2.
    private val SRVSVC = uuid("4b324fc8-1670-01d3-1278-5a47bf6ee188")
    private val NDR = uuid("8a885d04-1ceb-11c9-9fe8-08002b104860")

    private fun uuid(s: String): ByteArray {
        val hex = s.replace("-", "")
        val b = ByteArray(16) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        // Первые три поля GUID — little-endian.
        fun swap(from: Int, len: Int) { for (i in 0 until len / 2) { val t = b[from + i]; b[from + i] = b[from + len - 1 - i]; b[from + len - 1 - i] = t } }
        swap(0, 4); swap(4, 2); swap(6, 2)
        return b
    }

    private fun header(ptype: Int, fragLen: Int, callId: Int): ByteBuffer =
        ByteBuffer.allocate(fragLen).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(5); put(0) // версия 5.0
            put(ptype.toByte()); put((FLAG_FIRST or FLAG_LAST).toByte())
            put(0x10); put(0); put(0); put(0) // представление данных: little-endian, ASCII, IEEE
            putShort(fragLen.toShort()); putShort(0) // длина фрагмента, длина аутентификации
            putInt(callId)
        }

    fun bind(callId: Int = 1): ByteArray {
        val len = 16 + 8 + 4 + 4 + 20 + 20 // заголовок, xmit/recv/группа, число контекстов, контекст, два синтаксиса
        return header(PTYPE_BIND, len, callId).apply {
            putShort(4280); putShort(4280); putInt(0) // max xmit/recv, assoc group
            put(1); put(0); putShort(0) // один контекст
            putShort(0); put(1); put(0) // context id 0, один синтаксис передачи
            put(SRVSVC); putShort(3); putShort(0)
            put(NDR); putInt(2)
        }.array()
    }

    /** Тело запроса NetrShareEnum (NDR) для сервера [server]. */
    fun shareEnumStub(server: String): ByteArray {
        val out = Ndr()
        val name = "\\\\$server"
        out.u32(0x00020000) // ServerName: unique-указатель
        out.wstring(name)
        out.u32(1) // InfoStruct.Level
        out.u32(1) // дискриминант объединения
        out.u32(0x00020004) // указатель на SHARE_INFO_1_CONTAINER
        out.u32(0) // EntriesRead
        out.u32(0) // Buffer = NULL
        out.u32(-1) // PreferedMaximumLength = MAX_PREFERRED_LENGTH
        out.u32(0x00020008) // ResumeHandle: unique-указатель
        out.u32(0)
        return out.bytes()
    }

    fun request(stub: ByteArray, callId: Int = 2): ByteArray {
        val len = 24 + stub.size
        return header(PTYPE_REQUEST, len, callId).apply {
            putInt(stub.size) // alloc hint
            putShort(0) // context id
            putShort(OPNUM_NETR_SHARE_ENUM.toShort())
            put(stub)
        }.array()
    }

    /**
     * Собирает тело ответа из фрагментов. [more] дочитывает данные канала, когда фрагмент пришёл не целиком.
     */
    fun responseStub(first: ByteArray, more: () -> ByteArray): ByteArray {
        var buf = first
        var pos = 0
        val stub = ByteArrayOutputStream()
        while (true) {
            while (buf.size < pos + 16) buf += more().also { if (it.isEmpty()) throw IOException("RPC: ответ оборван") }
            val b = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
            val ptype = buf[pos + 2].toInt()
            val flags = buf[pos + 3].toInt()
            val fragLen = b.getShort(pos + 8).toInt() and 0xFFFF
            while (buf.size < pos + fragLen) buf += more().also { if (it.isEmpty()) throw IOException("RPC: ответ оборван") }
            when (ptype) {
                PTYPE_RESPONSE -> stub.write(buf, pos + 24, fragLen - 24)
                PTYPE_FAULT -> throw IOException("RPC: сервер вернул ошибку 0x%08x".format(ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).getInt(pos + 24)))
                else -> throw IOException("RPC: неожиданный ответ (тип $ptype)")
            }
            pos += fragLen
            if (flags and FLAG_LAST != 0) break
        }
        return stub.toByteArray()
    }

    fun checkBindAck(resp: ByteArray) {
        if (resp.size < 16 || resp[2].toInt() != PTYPE_BIND_ACK) throw IOException("RPC: сервер отклонил привязку к srvsvc")
    }

    /** Разбор ответа NetrShareEnum уровня 1. */
    fun parseShareEnum(stub: ByteArray): List<SmbShareInfo> {
        val r = NdrReader(stub)
        val level = r.u32()
        if (level != 1) throw IOException("RPC: неожиданный уровень $level")
        r.u32() // дискриминант
        if (r.u32() == 0) return emptyList() // контейнер NULL
        val count = r.u32()
        if (r.u32() == 0) return emptyList() // буфер NULL
        val max = r.u32()
        if (max < count || count > 10_000) throw IOException("RPC: повреждённый ответ")
        val entries = List(count) { Triple(r.u32(), r.u32(), r.u32()) } // (ptr имени, тип, ptr комментария)
        val result = ArrayList<SmbShareInfo>(count)
        for ((namePtr, type, remarkPtr) in entries) {
            val name = if (namePtr != 0) r.wstring() else ""
            val remark = if (remarkPtr != 0) r.wstring() else ""
            result += SmbShareInfo(name, remark, type)
        }
        r.u32() // TotalEntries
        if (r.u32() != 0) r.u32() // ResumeHandle
        val status = r.u32()
        if (status != 0) throw IOException(if (status == 5) "Нет прав на список общих папок" else "Сервер вернул ошибку %d".format(status))
        return result
    }

    private class Ndr {
        private val out = ByteArrayOutputStream()
        fun u32(v: Int) {
            out.write(v and 0xFF); out.write((v ushr 8) and 0xFF); out.write((v ushr 16) and 0xFF); out.write((v ushr 24) and 0xFF)
        }
        fun wstring(s: String) {
            val n = s.length + 1
            u32(n); u32(0); u32(n)
            for (ch in s) { out.write(ch.code and 0xFF); out.write(ch.code ushr 8) }
            out.write(0); out.write(0)
            while (out.size() % 4 != 0) out.write(0)
        }
        fun bytes(): ByteArray = out.toByteArray()
    }

    private class NdrReader(private val b: ByteArray) {
        private val buf = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        private fun need(n: Int) { if (buf.remaining() < n) throw IOException("RPC: ответ короче ожидаемого") }
        fun u32(): Int { align(); need(4); return buf.getInt() }
        private fun align() { while (buf.position() % 4 != 0 && buf.hasRemaining()) buf.get() }
        fun wstring(): String {
            u32() // max count
            u32() // offset
            val actual = u32()
            if (actual < 0 || actual > 0x10000) throw IOException("RPC: повреждённая строка")
            need(actual * 2)
            val chars = CharArray(actual) { buf.getChar() }
            return String(chars).trimEnd('\u0000')
        }
    }

    /** Список шар сервера: подключается к IPC$ уже открытой сессии. */
    fun list(session: com.hierynomus.smbj.session.Session, server: String): List<SmbShareInfo> {
        val ipc = session.connectShare("IPC$") as? com.hierynomus.smbj.share.PipeShare
            ?: throw IOException("Сервер не дал доступ к IPC$")
        try {
            val pipe = ipc.open(
                "srvsvc",
                com.hierynomus.mssmb2.SMB2ImpersonationLevel.Impersonation,
                setOf(com.hierynomus.msdtyp.AccessMask.MAXIMUM_ALLOWED),
                null,
                com.hierynomus.mssmb2.SMB2ShareAccess.ALL,
                com.hierynomus.mssmb2.SMB2CreateDisposition.FILE_OPEN,
                null,
            )
            pipe.use { p ->
                checkBindAck(p.transact(bind()))
                val first = p.transact(request(shareEnumStub(server)))
                val stub = responseStub(first) {
                    val chunk = ByteArray(8192)
                    val n = p.read(chunk)
                    if (n <= 0) ByteArray(0) else chunk.copyOf(n)
                }
                return parseShareEnum(stub)
            }
        } finally {
            runCatching { ipc.close() }
        }
    }
}
