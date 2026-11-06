package client.wexmanager

import client.wexmanager.core.elf.Elf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ElfTest {
    // Фикстуры — .elf: файлы .so AGP не кладёт в java-ресурсы тестов.
    private fun res(name: String) = File(javaClass.getResource("/elf/$name")!!.toURI())

    @Test
    fun arm64With16kPages() {
        val e = Elf.parse(res("libjni16k.elf"))
        assertTrue(e.is64)
        assertEquals("AArch64 (arm64)", e.machine)
        assertEquals("libjnitest.so", e.soname)
        assertTrue(e.needed.toString(), e.needed.contains("libc.so"))
        assertEquals(16384L, e.loadAlignment)
        assertEquals(setOf("Java_com_wex_test_1app_Native_answer", "JNI_OnLoad"), e.jniFunctions.map { it.name }.toSet())
        assertTrue(e.symbols.any { it.name == "wex_exported_data" && it.type == "OBJECT" && it.defined })
        assertTrue(e.symbols.any { !it.defined }) // импорт из libc
        assertTrue(e.buildId != null)
    }

    @Test
    fun strippedX86With4kPages() {
        val e = Elf.parse(res("libjni4k.elf"))
        assertEquals("x86_64", e.machine)
        assertEquals(4096L, e.loadAlignment)
        assertTrue(e.stripped)
        assertTrue(e.jniFunctions.isNotEmpty()) // .dynsym остаётся и после strip
        assertFalse(Elf.isElf(File.createTempFile("notelf", ".so").apply { writeText("not elf") }))
    }
}
