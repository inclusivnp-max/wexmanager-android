package client.wexmanager

import client.wexmanager.feature.viewer.FontInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FontInfoTest {
    private val font = File("src/main/res/font/jetbrains_mono.ttf")

    @Test
    fun readsNameTable() {
        val names = FontInfo.read(font)
        assertNotNull(names)
        assertEquals("JetBrains Mono", names!!.family)
    }

    @Test
    fun coverageFromCmap() {
        val has = FontInfo.coverage(font)!!
        assertTrue(has('A'.code))
        assertTrue(has('Ж'.code))
        assertTrue(has('0'.code))
        assertFalse(has('中'.code))
    }
}
