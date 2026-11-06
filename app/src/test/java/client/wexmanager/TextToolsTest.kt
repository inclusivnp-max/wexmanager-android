package client.wexmanager

import client.wexmanager.feature.editor.TextTool
import client.wexmanager.feature.editor.TextTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TextToolsTest {
    private fun t(tool: TextTool, s: String) = TextTools.apply(tool, s)

    @Test
    fun caseConversions() {
        assertEquals("ПРИВЕТ WORLD", t(TextTool.Upper, "Привет world"))
        assertEquals("привет world", t(TextTool.Lower, "ПРИВЕТ World"))
        assertEquals("Привет Мир, It's Ok", t(TextTool.Capitalize, "привет мИР, it's ok"))
    }

    @Test
    fun lineOperationsKeepTrailingNewline() {
        assertEquals("a2\na10\nb\n", t(TextTool.SortAsc, "b\na10\na2\n"))
        assertEquals("b\na10\na2", t(TextTool.SortDesc, "a2\nb\na10"))
        assertEquals("x\ny\n", t(TextTool.Unique, "x\ny\nx\n"))
        assertEquals("3\n2\n1", t(TextTool.Reverse, "1\n2\n3"))
        assertEquals("a\nb\n", t(TextTool.RemoveEmpty, "a\n\n   \nb\n"))
        assertEquals("a\nb\t c\n", t(TextTool.TrimTrailing, "a  \nb\t c\t\n"))
    }

    @Test
    fun jsonFormatAndMinify() {
        val pretty = TextTools.apply(TextTool.JsonFormat, """{"b":1,"a":[true,null,12345678901234567890]}""", "  ")
        assertEquals("{\n  \"b\": 1,\n  \"a\": [\n    true,\n    null,\n    12345678901234567890\n  ]\n}", pretty)
        assertEquals("""{"b":1,"a":[true,null,12345678901234567890]}""", t(TextTool.JsonMinify, pretty))
    }

    @Test
    fun invalidJsonHasReadableError() {
        try {
            t(TextTool.JsonFormat, "{\"a\": }")
            fail()
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.startsWith("Некорректный JSON"))
        }
    }

    @Test
    fun encodings() {
        assertEquals("0J/RgNC40LLQtdGC", t(TextTool.Base64Encode, "Привет"))
        assertEquals("Привет", t(TextTool.Base64Decode, "0J/RgNC40LLQtdGC\n"))
        assertEquals("a+b%26c%3D%D1%8F", t(TextTool.UrlEncode, "a b&c=я"))
        assertEquals("a b&c=я", t(TextTool.UrlDecode, "a+b%26c%3D%D1%8F"))
        assertEquals("id \\u043f\\u0440\\u0438", t(TextTool.UnicodeEscape, "id при"))
        assertEquals("id при", t(TextTool.UnicodeUnescape, "id \\u043f\\u0440\\u0438"))
    }

    @Test
    fun validateJsonAndXml() {
        val parser = { org.kxml2.io.KXmlParser() }
        assertTrue(TextTools.validate("{\"a\": [1, 2]}", parser).ok)
        val bad = TextTools.validate("{\n  \"a\": 1,\n  \"b\": \n}", parser)
        assertTrue(!bad.ok)
        assertEquals(4, bad.line)
        assertTrue(TextTools.validate("<a><b x=\"1\"/></a>", parser).ok)
        val badXml = TextTools.validate("<a>\n<b>\n</a>", parser)
        assertTrue(!badXml.ok)
        assertEquals(3, badXml.line)
        assertTrue(!TextTools.validate("plain text", parser).ok)
    }

    @Test
    fun formatsXml() {
        val src = "<?xml version=\"1.0\"?><root><!-- c --><item  a=\"1\">text</item><empty/><list><x b='2'   /></list><![CDATA[<raw>]]></root>"
        val expected = """
<?xml version="1.0"?>
<root>
  <!-- c -->
  <item a="1">text</item>
  <empty/>
  <list>
    <x b='2' />
  </list>
  <![CDATA[<raw>]]>
</root>
""".trimIndent()
        assertEquals(expected, TextTools.formatXml(src, "  "))
    }

    @Test
    fun longTagsBreakAttributes() {
        val src = "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" android:layout_width=\"match_parent\" android:layout_height=\"wrap_content\"><TextView android:text=\"a > b\"/></LinearLayout>"
        val out = TextTools.formatXml(src, "    ")
        assertEquals(
            "<LinearLayout\n    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\">\n    <TextView android:text=\"a > b\"/>\n</LinearLayout>",
            out,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun unbalancedXmlFails() {
        TextTools.formatXml("<a><b></a>")
    }
}

