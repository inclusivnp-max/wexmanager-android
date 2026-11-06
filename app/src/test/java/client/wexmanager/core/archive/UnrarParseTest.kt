package client.wexmanager.core.archive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnrarParseTest {
    private val sample = """

UNRAR 7.23 freeware      Copyright (c) 1993-2026 Alexander Roshal

Archive: plain.rar
Details: RAR 5, solid

        Name: папка/sub/random.bin
        Type: File
        Size: 300000
 Packed size: 300622
       Ratio: 100%
       mtime: 2026-10-03 17:05:41,458656808
  Attributes: -rw-r--r--
       CRC32: CEFE42DA

        Name: link.txt
        Type: Unix symbolic link
      Target: папка/hello.txt
        Size: 0

        Name: secret.txt
        Type: File
        Size: 12
       Flags: encrypted solid

        Name: QO
        Type: Service
        Size: 129

        Name: папка
        Type: Directory
       mtime: 2026-10-03 17:05:41,456268847
"""

    @Test
    fun parsesTechnicalListing() {
        val e = Unrar.parseTechnical(sample)
        assertEquals(listOf("папка/sub/random.bin", "link.txt", "secret.txt", "папка"), e.map { it.name })
        assertEquals(300000L, e[0].size)
        assertTrue(e[0].time > 0)
        assertTrue(e[1].link)
        assertTrue(e[2].encrypted)
        assertFalse(e[0].encrypted)
        assertTrue(e[3].isDir)
    }

    @Test
    fun damagedNames() {
        val err = "папка/sub/random.bin - checksum error\nCRC failed in the encrypted file a b.txt. Corrupt file or wrong password.\nTotal errors: 2\n"
        assertEquals(listOf("папка/sub/random.bin", "a b.txt"), Unrar.damaged(err))
    }

    @Test
    fun skipsVolumeContinuations() {
        val text = """
        Name: big.bin
        Type: File
        Size: 400000
       Ratio: -->

        Name: big.bin
        Type: File
        Size: 400000
       Ratio: <->

        Name: big.bin
        Type: File
        Size: 400000
       Ratio: <--

        Name: small.txt
        Type: File
        Size: 18
       Ratio: 100%
"""
        assertEquals(listOf("big.bin" to 400000L, "small.txt" to 18L), Unrar.parseTechnical(text).map { it.name to it.size })
    }
}

