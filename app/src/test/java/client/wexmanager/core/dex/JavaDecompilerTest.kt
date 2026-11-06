package client.wexmanager.core.dex

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class JavaDecompilerTest {
    @Test
    fun decompilesClassFromDex() {
        val bytes = javaClass.getResourceAsStream("/dex/consts.dex")!!.use { it.readBytes() }
        val tmp = Files.createTempFile("wex", ".dex").toFile().apply { writeBytes(bytes) }
        val s = DexSession.open(tmp, emptyList())
        val cls = s.classes().first { '$' !in it.descriptor }
        val java = JavaDecompiler.decompile(bytes, cls.descriptor)
        val simple = cls.descriptor.substringAfterLast('/').removeSuffix(";")
        assertTrue(java, java.contains("class $simple") || java.contains("interface $simple") || java.contains("enum $simple"))
        File(System.getProperty("java.io.tmpdir"), "wex-jadx.txt").writeText(java)
    }
}
