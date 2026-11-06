package client.wexmanager

import client.wexmanager.feature.editor.lang.CompletionEngine
import client.wexmanager.feature.editor.lang.CompletionItem
import client.wexmanager.feature.editor.lang.CompletionRequest
import client.wexmanager.feature.editor.lang.ItemKind
import client.wexmanager.feature.editor.lang.Languages
import client.wexmanager.feature.editor.lang.SyntaxInfo
import client.wexmanager.feature.editor.lang.Tok
import client.wexmanager.feature.editor.lang.WordIndexer
import org.junit.Assert.*
import org.junit.Test

/** Подсказки Go в автономном режиме: только собственные и stdlib-источники, без чужого мусора. */
class GoCompletionTest {
    private val go = Languages.byId("go")!!

    /** Лёгкий SyntaxInfo для unit-теста: комментарии и строки по текущей строке. */
    private class TestSyntax(private val text: String, private val caret: Int) : SyntaxInfo {
        override fun tokenAt(offset: Int): Tok? {
            if (offset < 0 || offset >= text.length) return null
            val lineStart = text.lastIndexOf('\n', offset - 1).let { if (it < 0) 0 else it + 1 }
            val line = text.substring(lineStart, offset + 1)
            val slash = line.indexOf("//")
            if (slash >= 0 && slash < offset - lineStart) return Tok.COMMENT
            val quotes = line.count { it == '"' }
            if (quotes % 2 == 1) return Tok.STRING
            return null
        }
    }

    private fun complete(text: String, explicit: Boolean = false, caret: Int = text.length): List<CompletionItem> {
        val index = WordIndexer.build(text, go, 1)
        val syntax = TestSyntax(text, caret)
        val result = CompletionEngine.complete(CompletionRequest(text, caret, go, syntax, index, explicit, null))
        return result?.items ?: emptyList()
    }

    @Test
    fun dotMemberKeepsPackageFunctions() {
        val text = "package main\n\nimport \"fmt\"\n\nfunc main() {\n\tfmt.Println(\"hi\")\n}\n\nfmt.Pri"
        val items = complete(text)
        assertTrue("fmt.Println", items.any { it.label == "Println" && it.kind == ItemKind.FUNCTION })
        assertTrue("fmt.Printf", items.any { it.label == "Printf" })
        assertFalse("must not leak other languages", items.any { it.label == "println!" })
    }

    @Test
    fun packageMemberLimitsAreFromWorkplaceIndex() {
        val text = "package main\n\nimport \"encoding/json\"\n\nfunc main() {\n\tjson.Marshal(1)\n}\n\njson."
        val items = complete(text, explicit = true)
        assertTrue(items.any { it.label == "Marshal" || it.label == "Unmarshal" })
    }

    @Test
    fun fileSymbolsBeatGenericVocabulary() {
        val text = "package app\nfunc myHelper() int { return 42 }\nfunc main() { myH"
        val items = complete(text)
        assertTrue(items.any { it.label == "myHelper" && it.kind == ItemKind.FUNCTION })
    }

    @Test
    fun goSnippetWorks() {
        val items = complete("package main\nfunc m(){ iferr", explicit = true)
        assertTrue(items.any { it.isSnippet && it.label == "iferr" })
    }

    @Test
    fun noCompletionInsideCommentsAndStrings() {
        val insideComment = "package p\nfunc f() {\n// fm"
        val itemsInComment = complete(insideComment, explicit = false)
        assertTrue(itemsInComment.isEmpty())
        val insideString = "package p\nvar s = \"abc"
        assertTrue(complete(insideString, explicit = false).isEmpty())
    }

    @Test
    fun importSuggestionsStayGoOnly() {
        val text = "package main\nimport \"fm"
        val result = complete(text)
        assertTrue(result.any { it.label.startsWith("fmt") })
        assertFalse(result.any { it.label == "func" || it.label == "package" })
    }

    @Test
    fun methodAndFieldSymbolsUseReceiverOwner() {
        val text = """
            package p
            type User struct { Name string }
            func (u *User) Greet() string { return "hi" }
            func main() { u := User{}; u.
        """.trimIndent()
        val index = WordIndexer.build(text, go, 1)
        assertTrue(index.extra.any { it.text == "Greet" && it.detail.contains("User") })
        assertTrue(index.extra.any { it.text == "Name" && it.detail.contains("User") })
        val items = complete(text, explicit = true)
        assertTrue(items.any { it.label == "Greet" })
        assertTrue(items.any { it.label == "Name" })
    }
}
