package client.wexmanager

import client.wexmanager.core.network.SmbShares
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class SmbSharesTest {
    private class W {
        val out = ByteArrayOutputStream()
        fun u32(v: Int) = apply { for (i in 0 until 4) out.write((v ushr (8 * i)) and 0xFF) }
        fun ws(s: String) = apply {
            val n = s.length + 1
            u32(n); u32(0); u32(n)
            for (c in s) { out.write(c.code and 0xFF); out.write(c.code ushr 8) }
            out.write(0); out.write(0)
            while (out.size() % 4 != 0) out.write(0)
        }
    }

    @Test
    fun parsesShareEnumLevel1() {
        val w = W().u32(1).u32(1).u32(0x20000) // уровень, дискриминант, указатель на контейнер
            .u32(3).u32(0x20004).u32(3) // EntriesRead, указатель на массив, max count
            .u32(0x20008).u32(0).u32(0x2000c) // Public: диск
            .u32(0x20010).u32(0x80000003.toInt()).u32(0x20014) // IPC$: служебный
            .u32(0x20018).u32(0).u32(0) // Docs без комментария
            .ws("Public").ws("Общие файлы")
            .ws("IPC$").ws("Remote IPC")
            .ws("Docs")
            .u32(3).u32(0x2001c).u32(0).u32(0) // TotalEntries, ResumeHandle, WERROR
        val shares = SmbShares.parseShareEnum(w.out.toByteArray())
        assertEquals(listOf("Public", "IPC$", "Docs"), shares.map { it.name })
        assertEquals("Общие файлы", shares[0].remark)
        assertTrue(shares[0].isDisk)
        assertFalse(shares[1].isDisk)
        assertEquals("", shares[2].remark)
    }

    @Test
    fun requestHasExpectedFraming() {
        val bind = SmbShares.bind()
        assertEquals(72, bind.size)
        assertEquals(11, bind[2].toInt()) // bind
        val req = SmbShares.request(SmbShares.shareEnumStub("srv"))
        assertEquals(0, req[2].toInt()) // request
        assertEquals(req.size, (req[8].toInt() and 0xFF) or ((req[9].toInt() and 0xFF) shl 8))
        assertEquals(15, req[22].toInt()) // opnum NetrShareEnum
    }
}
