package client.wexmanager.feature.compare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseExtensionsTest {
    @Test
    fun commaSeparatedNames() {
        assertEquals(setOf("apk", "jpg"), parseExtensions("apk, jpg"))
    }

    @Test
    fun starAndDotPrefixesAreTheSameExtension() {
        assertEquals(setOf("so"), parseExtensions("*.so"))
        assertEquals(setOf("so"), parseExtensions(".so"))
        assertEquals(parseExtensions("*.so"), parseExtensions(".so"))
    }

    @Test
    fun caseInsensitive() {
        assertEquals(setOf("apk", "jpg", "so"), parseExtensions("APK, Jpg, *.SO"))
    }

    @Test
    fun emptyMeansEmptySet() {
        assertTrue(parseExtensions("").isEmpty())
        assertTrue(parseExtensions("   ").isEmpty())
        assertTrue(parseExtensions(",").isEmpty())
        assertEquals(emptySet<String>(), parseExtensions(""))
    }
}
