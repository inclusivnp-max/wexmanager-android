package client.wexmanager.core.dex

import com.tim.csmali.Smali
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class ClassFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun ownClass(): ByteArray = javaClass.getResourceAsStream("ClassFilesTest.class")!!.use { it.readBytes() }

    @Test
    fun detectsClassMagic() {
        val f = tmp.newFile("A.class").apply { writeBytes(ownClass()) }
        assertTrue(ClassFiles.isClass(f))
        // Универсальный Mach-O: та же сигнатура, но дальше число архитектур.
        val fat = tmp.newFile("fat").apply { writeBytes(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte(), 0, 0, 0, 2)) }
        assertFalse(ClassFiles.isClass(fat))
    }

    @Test
    fun textRoundTripIsStable() {
        val f = tmp.newFile("B.class").apply { writeBytes(ownClass()) }
        val text = Smali.classToSmali(f)
        val rebuilt = tmp.newFile("C.class").apply { writeBytes(ClassFiles.encode(text)) }
        assertTrue(ClassFiles.isClass(rebuilt))
        assertEquals(text, Smali.classToSmali(rebuilt))
    }

    @Test(expected = IOException::class)
    fun syntaxErrorIsIoException() {
        ClassFiles.encode(".class public Lbroken;\n.method public x()V\n    bogus_op\n.end method\n")
    }
}
