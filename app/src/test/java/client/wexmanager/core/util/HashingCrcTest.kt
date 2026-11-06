package client.wexmanager.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class HashingCrcTest {
    @Test
    fun knownCrc32Of123456789() {
        val bytes = "123456789".toByteArray(Charsets.US_ASCII)
        assertEquals("cbf43926", Hashing.crc32(bytes))
    }

    @Test
    fun streamingFileMatchesBytes() {
        val bytes = "123456789".toByteArray(Charsets.US_ASCII)
        val file = File.createTempFile("crc32", ".bin").apply {
            writeBytes(bytes)
            deleteOnExit()
        }
        assertEquals("cbf43926", Hashing.crc32(file))
    }
}
