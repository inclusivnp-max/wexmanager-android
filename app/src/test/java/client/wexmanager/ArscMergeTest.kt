package client.wexmanager

import client.wexmanager.core.apk.ArscMerge
import client.wexmanager.core.apk.ArscTable
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ArscMergeTest {
    private fun bytes(name: String) = javaClass.getResourceAsStream("/arsc/split/$name.arsc")!!.readBytes()

    @Test
    fun stylesRoundTripByteExact() {
        for (n in listOf("base", "ru", "xxhdpi")) assertArrayEquals(n, bytes(n), ArscTable.parse(bytes(n)).encode())
    }

    @Test
    fun mergesConfigSplits() {
        val base = ArscTable.parse(bytes("base"))
        ArscMerge.merge(base, ArscTable.parse(bytes("ru")))
        ArscMerge.merge(base, ArscTable.parse(bytes("xxhdpi")))
        val t = ArscTable.parse(base.encode())
        val pkg = t.packages.single()
        val stringType = pkg.typeIdOf("string")!!
        fun value(key: String, qualifier: String): String {
            val chunk = pkg.types(stringType).first { it.config.qualifier() == qualifier }
            val e = chunk.entries.filterNotNull().first { pkg.key(it) == key }
            return t.format(e.value)
        }
        // de и mdpi aapt2 оставил в базе, ru и xxhdpi пришли из частей.
        assertEquals(listOf("", "de", "mdpi", "ru", "xxhdpi"), pkg.types(stringType).map { it.config.qualifier() }.sorted())
        assertEquals("lang:default", value("greeting", ""))
        assertEquals("lang:ru-split", value("greeting", "ru"))
        assertEquals("dens:xxhdpi-split", value("dens", "xxhdpi"))
        assertEquals("Привет жирный", value("styled", "ru"))
        // Стиль строки перенесён вместе с ней: <b> на «жирный».
        val ru = pkg.types(stringType).first { it.config.qualifier() == "ru" }
        val styled = ru.entries.filterNotNull().first { pkg.key(it) == "styled" }
        assertEquals(listOf(Triple("b", 7, 12)), t.strings.spans(styled.value.data))
        // Повторное слияние той же части ничего не дублирует.
        val again = ArscTable.parse(base.encode())
        ArscMerge.merge(again, ArscTable.parse(bytes("ru")))
        assertEquals(5, again.packages.single().types(stringType).size)
    }
}
