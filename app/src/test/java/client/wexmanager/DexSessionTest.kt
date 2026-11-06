package client.wexmanager

import client.wexmanager.core.dex.DexSession
import client.wexmanager.core.dex.SmaliEditException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DexSessionTest {
    private fun fixture(): File {
        val tmp = Files.createTempFile("wex", ".dex").toFile()
        javaClass.getResourceAsStream("/dex/consts.dex")!!.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        return tmp
    }

    @Test
    fun editClassAndRoundTrip() {
        val dex = fixture()
        val s = DexSession.open(dex, emptyList())
        val cls = s.classes().first()
        val text = s.smali(cls.descriptor)
        assertTrue(text.startsWith(".class"))
        // Новое поле в классе: после сборки и повторного чтения оно на месте.
        val edited = text.replaceFirst("\n", "\n\n.field public static wexMarker:I\n")
        s.apply(cls.descriptor, edited)
        assertEquals(setOf(0), s.modifiedUnits)
        val built = s.build()
        dex.writeBytes(built.values.single())
        val again = DexSession.open(dex, emptyList())
        assertTrue(again.smali(cls.descriptor).contains("wexMarker"))
    }

    @Test
    fun partialRebuildKeepsOtherClasses() {
        val dex = fixture()
        val s = DexSession.open(dex, emptyList())
        val all = s.classes()
        val before = all.associate { it.descriptor to s.smali(it.descriptor) }
        val target = all.first()
        s.apply(target.descriptor, before.getValue(target.descriptor).replaceFirst("\n", "\n\n.field public static wexMarker:I\n"))
        val out = s.build().values.single()
        // Только новое поле и его строки: пересобран один класс, а не весь DEX.
        assertTrue("размер ${out.size} при исходном ${dex.length()}", out.size - dex.length() < 512)
        dex.writeBytes(out)
        val again = DexSession.open(dex, emptyList())
        assertEquals(all.map { it.descriptor }.toSet(), again.classes().map { it.descriptor }.toSet())
        for (c in all) if (c.descriptor != target.descriptor) assertEquals(before[c.descriptor], again.smali(c.descriptor))
    }

    @Test
    fun syntaxErrorReportsLine() {
        val s = DexSession.open(fixture(), emptyList())
        val cls = s.classes().first()
        try {
            s.apply(cls.descriptor, s.smali(cls.descriptor) + "\n.method public broken()V\n    invoke-virtual-garbage v0\n.end method\n")
            fail("ожидалась ошибка")
        } catch (e: SmaliEditException) {
            assertTrue(e.message!!.isNotBlank())
        }
        assertTrue(s.modifiedUnits.isEmpty())
    }

    @Test
    fun dexInsideZip() {
        val dex = fixture()
        val apk = Files.createTempFile("wex", ".apk").toFile()
        ZipOutputStream(apk.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("classes.dex")); z.write(dex.readBytes()); z.closeEntry()
            z.putNextEntry(ZipEntry("classes2.dex")); z.write(dex.readBytes()); z.closeEntry()
        }
        assertEquals(listOf("classes.dex", "classes2.dex"), DexSession.dexEntries(apk))
        val s = DexSession.open(apk, DexSession.dexEntries(apk))
        assertEquals(2, s.units.size)
        val created = s.create("Lwex/NewOne;", 1)
        assertEquals(1, created.dexIndex)
        assertEquals(setOf("classes2.dex"), s.build().keys)
    }
}
