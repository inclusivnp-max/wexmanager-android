package client.wexmanager

import client.wexmanager.core.apk.ArscTable
import client.wexmanager.core.apk.ResValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArscTest {
    private fun table() = ArscTable.parse(javaClass.getResourceAsStream("/arsc/easteregg.arsc")!!.readBytes())

    @Test
    fun parsesPackageTypesAndStrings() {
        val t = table()
        val pkg = t.packages.single()
        assertEquals("com.android.egg", pkg.name)
        assertTrue(pkg.typeNames.contains("string"))
        val strings = pkg.types(pkg.typeIdOf("string")!!)
        val default = strings.first { it.config.isDefault() }
        val e = default.entries.filterNotNull().first { !it.complex }
        assertEquals(ResValue.TYPE_STRING, e.value.type)
        assertNotNull(t.strings[e.value.data])
    }

    @Test
    fun editAndTranslateRoundTrip() {
        val t = table()
        val pkg = t.packages.single()
        val typeId = pkg.typeIdOf("string")!!
        val default = pkg.types(typeId).first { it.config.isDefault() }
        val e = default.entries.filterNotNull().first { !it.complex && it.value.type == ResValue.TYPE_STRING }
        val name = pkg.key(e)
        t.setValue(e, "Привет из WexManager")
        t.translate(pkg, "eo", mapOf(e.index to "Saluton"))
        val again = ArscTable.parse(t.encode())
        val p2 = again.packages.single()
        val d2 = p2.types(typeId).first { it.config.isDefault() }
        val e2 = d2.entries[e.index]!!
        assertEquals(name, p2.key(e2))
        assertEquals("Привет из WexManager", again.format(e2.value))
        val eo = p2.types(typeId).first { it.config.qualifier() == "eo" }
        assertEquals("Saluton", again.format(eo.entries[e.index]!!.value))
        // Остальные строки не задеты.
        val other = d2.entries.filterNotNull().first { it.index != e.index && !it.complex && it.value.type == ResValue.TYPE_STRING }
        assertEquals(t.format(default.entries[other.index]!!.value), again.format(other.value))
    }

    @Test
    fun parsesTypedValues() {
        assertEquals(ResValue.TYPE_INT_COLOR_RGB8 to 0xFF112233.toInt(), ResValue.parse(ResValue.TYPE_INT_COLOR_ARGB8, "#112233"))
        assertEquals(ResValue.TYPE_INT_BOOLEAN to -1, ResValue.parse(ResValue.TYPE_INT_BOOLEAN, "true"))
        val (t, d) = ResValue.parse(ResValue.TYPE_DIMENSION, "16dp")
        assertEquals("16dp", ResValue(t, d).format(table().strings))
    }
}
