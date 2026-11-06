package client.wexmanager

import client.wexmanager.core.archive.ArchiveEngine
import client.wexmanager.core.archive.ArchiveFormat
import client.wexmanager.core.archive.ArchivePasswordException
import client.wexmanager.core.ops.ConflictChoice
import client.wexmanager.core.ops.ConflictDecision
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.random.Random

class ArchiveEngineTest {
    private lateinit var root: File
    private lateinit var src: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("wex-arch").toFile()
        src = File(root, "src").apply { mkdirs() }
        File(src, "hello.txt").writeText("Привет, архив!\n".repeat(50))
        File(src, "sub").mkdirs()
        File(src, "sub/data.bin").writeBytes(Random(42).nextBytes(70_000))
        File(src, "sub/empty").mkdirs()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun roundTrip(format: ArchiveFormat, level: Int, password: String? = null) = runBlocking {
        val archive = File(root, "out.${format.extension}")
        ArchiveEngine.compress(listOf(src), archive, format, level, password) { _, _ -> }
        assertTrue("архив создан", archive.length() > 0)
        assertFalse("временный файл убран", File(root, ".${archive.name}.part").exists())
        val dest = File(root, "dest-${format.name}-$level")
        ArchiveEngine.extract(archive, dest, null, password, onConflict = { _, _ -> fail("конфликтов быть не должно"); error("") }) { _, _ -> }
        assertEquals(File(src, "hello.txt").readText(), File(dest, "src/hello.txt").readText())
        assertArrayEquals(File(src, "sub/data.bin").readBytes(), File(dest, "src/sub/data.bin").readBytes())
        assertTrue("пустая папка сохранена", File(dest, "src/sub/empty").isDirectory || format == ArchiveFormat.SevenZ)
        assertTrue("нет недописанных файлов", dest.walk().none { it.name.startsWith(".wex-") })
    }

    @Test fun zipDeflate() = roundTrip(ArchiveFormat.Zip, 6)

    @Test fun zipStore() = roundTrip(ArchiveFormat.Zip, 0)

    @Test fun zipAesWithStore() = roundTrip(ArchiveFormat.Zip, 0, "секрет-123")

    @Test fun zipAesDeflate() = roundTrip(ArchiveFormat.Zip, 9, "pass")

    @Test fun sevenZ() = roundTrip(ArchiveFormat.SevenZ, 5)

    @Test fun tarGz() = roundTrip(ArchiveFormat.TarGz, 6)

    @Test
    fun wrongZipPasswordKeepsExistingFile() = runBlocking {
        val archive = File(root, "locked.zip")
        ArchiveEngine.compress(listOf(src), archive, ArchiveFormat.Zip, 6, "right") { _, _ -> }
        val dest = File(root, "dest").apply { mkdirs() }
        val existing = File(dest, "src/hello.txt").apply { parentFile!!.mkdirs(); writeText("оригинал") }
        try {
            ArchiveEngine.extract(archive, dest, null, "wrong", onConflict = { _, _ -> ConflictDecision(ConflictChoice.Replace, true) }) { _, _ -> }
            fail("ожидалась ошибка пароля")
        } catch (e: ArchivePasswordException) {
            assertTrue(e.wrong)
        }
        assertEquals("при ошибке существующий файл не тронут", "оригинал", existing.readText())
        assertTrue(dest.walk().none { it.name.startsWith(".wex-") })
    }

    @Test
    fun missingZipPasswordIsReported() = runBlocking {
        val archive = File(root, "locked.zip")
        ArchiveEngine.compress(listOf(src), archive, ArchiveFormat.Zip, 6, "right") { _, _ -> }
        try {
            ArchiveEngine.extract(archive, File(root, "d2"), null, null, onConflict = { _, _ -> ConflictDecision(ConflictChoice.Skip, true) }) { _, _ -> }
            fail("ожидался запрос пароля")
        } catch (e: ArchivePasswordException) {
            assertFalse(e.wrong)
        }
    }

    @Test
    fun cannotCompressIntoItself() = runBlocking {
        try {
            ArchiveEngine.compress(listOf(src), File(src, "self.zip"), ArchiveFormat.Zip, 6, null) { _, _ -> }
            fail("архив внутри источника должен быть запрещён")
        } catch (e: java.io.IOException) {
            assertFalse(File(src, "self.zip").exists())
        }
    }

    @Test
    fun verifyDetectsCorruption() = runBlocking {
        val good = File(root, "good.zip")
        ArchiveEngine.compress(listOf(src), good, ArchiveFormat.Zip, 0, null) { _, _ -> } // STORE — данные лежат как есть
        assertTrue(ArchiveEngine.verify(good, null) { _, _ -> }.isEmpty())
        // Портим байты внутри данных hello.txt (текст повторяется — найдём его в архиве).
        val bytes = good.readBytes()
        val marker = "Привет, архив!".toByteArray()
        val at = (0..bytes.size - marker.size).first { i -> marker.indices.all { bytes[i + it] == marker[it] } }
        bytes[at] = (bytes[at] + 1).toByte()
        val broken = File(root, "broken.zip").apply { writeBytes(bytes) }
        val bad = ArchiveEngine.verify(broken, null) { _, _ -> }
        assertEquals(1, bad.size)
        assertTrue(bad[0], bad[0].contains("hello.txt"))
    }

    @Test
    fun verifyPassesOtherFormats() = runBlocking {
        for (f in listOf(ArchiveFormat.TarGz, ArchiveFormat.SevenZ)) {
            val a = File(root, "ok.${f.extension}")
            ArchiveEngine.compress(listOf(src), a, f, 5, null) { _, _ -> }
            assertTrue(f.name, ArchiveEngine.verify(a, null) { _, _ -> }.isEmpty())
        }
    }

    /** ZIP «Проводника» Windows: имена в CP866 без флага UTF-8 — при русском языке устройства читаются правильно. */
    @Test
    fun legacyCp866NamesUnderRussianLocale() = runBlocking {
        val archive = File(root, "win.zip")
        java.util.zip.ZipOutputStream(archive.outputStream(), java.nio.charset.Charset.forName("IBM866")).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("Документы/Привет.txt"))
            z.write("текст".toByteArray())
            z.closeEntry()
        }
        val saved = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ru-RU"))
            val index = ArchiveEngine.index(archive)
            assertEquals(listOf("Документы"), index.list("")!!.map { it.name })
            assertEquals(listOf("Привет.txt"), index.list("Документы")!!.map { it.name })
            val dest = File(root, "win-out")
            ArchiveEngine.extract(archive, dest, null, null, onConflict = { _, _ -> error("") }) { _, _ -> }
            assertEquals("текст", File(dest, "Документы/Привет.txt").readText())
        } finally {
            java.util.Locale.setDefault(saved)
        }
    }

    @Test
    fun utf8NamesStayUtf8() = runBlocking {
        val archive = File(root, "utf.zip")
        java.util.zip.ZipOutputStream(archive.outputStream()).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("日本/Привет.txt")); z.write(1); z.closeEntry()
        }
        assertEquals(listOf("Привет.txt"), ArchiveEngine.index(archive).list("日本")!!.map { it.name })
    }
}

