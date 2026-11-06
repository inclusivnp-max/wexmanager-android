package client.wexmanager

import client.wexmanager.core.archive.ArchiveEngine
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.CRC32

class ZipAlignTest {
    private fun stored(zos: ZipArchiveOutputStream, name: String, data: ByteArray) {
        val e = ZipArchiveEntry(name)
        e.method = ZipArchiveEntry.STORED
        e.size = data.size.toLong()
        e.crc = CRC32().apply { update(data) }.value
        zos.putArchiveEntry(e); zos.write(data); zos.closeArchiveEntry()
    }

    @Test
    fun replacedEntriesKeepMethodAndStoredDataStaysAligned() = runBlocking {
        val apk = Files.createTempFile("wex", ".apk").toFile()
        ZipArchiveOutputStream(apk).use { zos ->
            stored(zos, "classes.dex", ByteArray(1001) { 1 })
            stored(zos, "resources.arsc", ByteArray(333) { 2 })
            stored(zos, "lib/arm64-v8a/libx.so", ByteArray(5000) { 3 })
            zos.putArchiveEntry(ZipArchiveEntry("AndroidManifest.xml")); zos.write(ByteArray(100) { 4 }); zos.closeArchiveEntry()
        }
        val newDex = Files.createTempFile("wex", ".dex").toFile().apply { writeBytes(ByteArray(777) { 9 }) }
        val newArsc = Files.createTempFile("wex", ".arsc").toFile().apply { writeBytes(ByteArray(55) { 8 }) }
        ArchiveEngine.modifyZip(apk, mapOf("classes.dex" to newDex, "resources.arsc" to newArsc))
        ZipFile.Builder().setFile(apk).get().use { z ->
            val dex = z.getEntry("classes.dex")
            assertEquals(ZipArchiveEntry.STORED, dex.method)
            assertEquals(777L, dex.size)
            val arsc = z.getEntry("resources.arsc")
            assertEquals(ZipArchiveEntry.STORED, arsc.method)
            assertEquals(0L, arsc.dataOffset % 4)
            val so = z.getEntry("lib/arm64-v8a/libx.so")
            assertEquals(ZipArchiveEntry.STORED, so.method)
            assertEquals(0L, so.dataOffset % 16384)
            assertEquals(5000, z.getInputStream(so).use { it.readBytes() }.size)
        }
    }
}
