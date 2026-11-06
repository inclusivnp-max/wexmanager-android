package client.wexmanager

import client.wexmanager.core.util.HashAlgorithm
import client.wexmanager.core.util.Hashing
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class HashingTest {
    @Test
    fun knownDigestsOfAbc() = runBlocking {
        val f = File.createTempFile("hash", ".txt").apply { writeText("abc"); deleteOnExit() }
        val r = Hashing.compute(f, HashAlgorithm.entries)
        assertEquals("900150983cd24fb0d6963f7d28e17f72", r[HashAlgorithm.MD5])
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", r[HashAlgorithm.SHA1])
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", r[HashAlgorithm.SHA256])
        assertEquals("352441c2", r[HashAlgorithm.CRC32])
    }

    @Test
    fun emptyFile() = runBlocking {
        val f = File.createTempFile("empty", ".bin").apply { deleteOnExit() }
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", Hashing.compute(f, listOf(HashAlgorithm.MD5))[HashAlgorithm.MD5])
    }
}
