package client.wexmanager

import client.wexmanager.core.apk.AxmlDecoder
import client.wexmanager.core.apk.AxmlEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.kxml2.io.KXmlParser

class AxmlRoundTripTest {
    private val original = javaClass.getResourceAsStream("/axml/manifest_wex.bin")!!.readBytes()

    private fun encode(text: String, hintsFrom: ByteArray = original): ByteArray =
        AxmlEncoder.encode(text, KXmlParser(), AxmlEncoder.Hints.from(AxmlDecoder.decode(hintsFrom)))

    @Test
    fun decodeEncodeDecodeIsStable() {
        val text = AxmlDecoder.decode(original).toXml()
        val again = AxmlDecoder.decode(encode(text)).toXml()
        assertEquals(text, again)
    }

    @Test
    fun editsKeepTypes() {
        val text = AxmlDecoder.decode(original).toXml()
        assertTrue(text.contains("android:versionName="))
        val edited = text
            .replace(Regex("android:versionName=\"[^\"]*\""), "android:versionName=\"42\"")
            .replace(Regex("android:versionCode=\"[^\"]*\""), "android:versionCode=\"777\"")
        val doc = AxmlDecoder.decode(encode(edited))
        val attrs = doc.root.attrs.associateBy { it.qualifiedName }
        assertEquals("42", attrs.getValue("android:versionName").value)
        assertEquals(AxmlEncoder.TYPE_STRING, attrs.getValue("android:versionName").type) // строка, а не число
        assertEquals("777", attrs.getValue("android:versionCode").value)
        assertEquals(AxmlEncoder.TYPE_INT_DEC, attrs.getValue("android:versionCode").type)
    }

    @Test
    fun badXmlReportsLine() {
        try {
            encode("<manifest>\n<application>\n</manifest>")
            fail()
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message, e.message!!.contains("строке"))
        }
    }

    private fun complexToFloat(c: Int): Float {
        val shift = intArrayOf(0, 7, 15, 23)[(c shr 4) and 3]
        return (c and 0xffffff00.toInt()).toFloat() / 256f / (1 shl shift)
    }

    @Test
    fun complexValuesArePrecise() {
        fun dim(v: String) = complexToFloat(AxmlEncoder.complex(v, AxmlEncoder.DIMEN_UNITS)!!)
        fun frac(v: String) = complexToFloat(AxmlEncoder.complex(v, AxmlEncoder.FRACTION_UNITS)!!) * 100f
        assertEquals(1.5f, dim("1.5dp"), 1e-4f)
        assertEquals(16f, dim("16dp"), 1e-4f)
        assertEquals(-0.5f, dim("-0.5sp"), 1e-4f)
        assertEquals(50f, frac("50%"), 1e-3f)
        assertEquals(-50f, frac("-50%"), 1e-3f)
        assertEquals(33.3f, frac("33.3%p"), 1e-2f)
    }

    private val ids = mapOf("name" to 0x01010003, "value" to 0x01010024, "theme" to 0x01010000)

    private fun encodeRaw(text: String, hints: AxmlEncoder.Hints = AxmlEncoder.Hints.EMPTY) =
        AxmlEncoder.encode(text, KXmlParser(), hints) { ids[it] ?: 0 }

    @Test
    fun metaDataTypesSurviveInsertedElements() {
        val ns = "xmlns:android=\"http://schemas.android.com/apk/res/android\""
        fun manifest(extra: String) = """<manifest $ns package="x">$extra<application>
            <meta-data android:name="flag" android:value="true"/>
            <meta-data android:name="count" android:value="5"/>
            <meta-data android:name="title" android:value="hello"/>
            </application></manifest>"""
        val original = encodeRaw(manifest(""))
        val hints = AxmlEncoder.Hints.from(AxmlDecoder.decode(original))
        val edited = AxmlDecoder.decode(encodeRaw(manifest("<uses-permission android:name=\"p\"/>"), hints))
        val metas = edited.root.findAll("meta-data").associate { e -> e.attr("name") to e.attrs.first { it.name == "value" }.type }
        assertEquals(AxmlEncoder.TYPE_INT_BOOLEAN, metas["flag"])
        assertEquals(AxmlEncoder.TYPE_INT_DEC, metas["count"])
        assertEquals(AxmlEncoder.TYPE_STRING, metas["title"])
    }

    @Test
    fun resourceNamesAreRejected() {
        try {
            encodeRaw("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"><application android:theme=\"@style/AppTheme\"/></manifest>")
            fail()
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message, e.message!!.contains("имена ресурсов"))
        }
    }

    @Test
    fun changedStringToReferenceAndNewDimensions() {
        val ns = "xmlns:android=\"http://schemas.android.com/apk/res/android\""
        val ids2 = mapOf("label" to 0x01010001, "layout_width" to 0x010100f4, "alpha" to 0x0101031f)
        fun enc(t: String, h: AxmlEncoder.Hints = AxmlEncoder.Hints.EMPTY) = AxmlEncoder.encode(t, KXmlParser(), h) { ids2[it] ?: 0 }
        val original = enc("<a $ns android:label=\"My app\"/>")
        val hints = AxmlEncoder.Hints.from(AxmlDecoder.decode(original))
        val ref = AxmlDecoder.decode(enc("<a $ns android:label=\"@0x7f120000\"/>", hints)).root.attrs.first()
        assertEquals(AxmlEncoder.TYPE_REFERENCE, ref.type)
        val dims = AxmlDecoder.decode(enc("<a $ns android:layout_width=\"16dp\" android:alpha=\"0.5\"/>")).root.attrs.associateBy { it.name }
        assertEquals(AxmlEncoder.TYPE_DIMENSION, dims.getValue("layout_width").type)
        assertEquals(AxmlEncoder.TYPE_FLOAT, dims.getValue("alpha").type)
    }
}
