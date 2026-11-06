package client.wexmanager.feature.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewTest {
    @Test
    fun csvQuotesSeparatorsAndNewlines() {
        val rows = Csv.parse("a,b,c\r\n\"x, y\",\"he said \"\"hi\"\"\",\"two\nlines\"\nlast,,\n", ',')
        assertEquals(listOf("a", "b", "c"), rows[0])
        assertEquals(listOf("x, y", "he said \"hi\"", "two\nlines"), rows[1])
        assertEquals(listOf("last", "", ""), rows[2])
        assertEquals(3, rows.size)
    }

    @Test
    fun csvWithoutTrailingNewlineAndTabs() {
        assertEquals(listOf(listOf("1", "2"), listOf("3", "4")), Csv.parse("1\t2\n3\t4", '\t'))
        assertEquals(2, Csv.parse("a\nb\nc\nd", ',', maxRows = 2).size)
    }

    @Test
    fun markdownGfmAndEscapedHtml() {
        val html = MarkdownHtml.render("| a | b |\n|---|---|\n| 1 | 2 |\n\n- [x] done\n\n~~old~~ <script>x()</script> https://example.com")
        assertTrue(html, html.contains("<table>"))
        assertTrue(html, html.contains("type=\"checkbox\""))
        assertTrue(html, html.contains("<del>old</del>"))
        assertTrue(html, html.contains("href=\"https://example.com\""))
        assertFalse(html, html.contains("<script>"))
    }

    @Test
    fun kinds() {
        assertEquals(PreviewKind.Markdown, previewKindFor("README.MD"))
        assertEquals(PreviewKind.Table, previewKindFor("x.tsv"))
        assertEquals(PreviewKind.Html, previewKindFor("index.htm"))
        assertEquals(null, previewKindFor("a.txt"))
    }
}
