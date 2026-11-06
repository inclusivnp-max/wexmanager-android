package client.wexmanager

import client.wexmanager.debug.LangSmoke
import client.wexmanager.feature.editor.lang.CompletionEngine
import client.wexmanager.feature.editor.lang.CompletionRequest
import client.wexmanager.feature.editor.lang.ItemKind
import client.wexmanager.feature.editor.lang.Languages
import client.wexmanager.feature.editor.lang.SnippetExpander
import client.wexmanager.feature.editor.lang.Tok
import client.wexmanager.feature.editor.lang.Token
import client.wexmanager.feature.editor.lang.WordIndexer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorLangTest {
    @Test
    fun allLanguagesSurviveSmoke() {
        val errors = LangSmoke.run()
        assertTrue(errors.joinToString("\n"), errors.isEmpty())
    }

    private fun tokens(langId: String, line: String, state: Int = 0): List<Token> =
        ArrayList<Token>().also { Languages.byId(langId)!!.lexer.tokenize(line, state, it) }

    private fun typeAt(tokens: List<Token>, col: Int): Tok? = tokens.lastOrNull { col >= it.start && col < it.end }?.type

    @Test
    fun kotlinTokens() {
        val line = "val s = \"hi\" // c"
        val t = tokens("kotlin", line)
        assertEquals(Tok.KEYWORD, typeAt(t, 0))
        assertEquals(Tok.STRING, typeAt(t, line.indexOf('"') + 1))
        assertEquals(Tok.COMMENT, typeAt(t, line.indexOf("//") + 3))
    }

    @Test
    fun smaliOpcodesFromClazzpatcherTables() {
        for (op in listOf("move-result-object", "invoke-polymorphic/range", "const-method-handle", "iget-quick", "rsub-int/lit8")) {
            val t = tokens("smali", "    $op v0")
            assertEquals(op, Tok.OPCODE, typeAt(t, 5))
        }
    }

    @Test
    fun blockCommentCarriesState() {
        val lexer = Languages.byId("java")!!.lexer
        val first = ArrayList<Token>()
        val state = lexer.tokenize("int a; /* open", 0, first)
        assertTrue(state != 0)
        val second = ArrayList<Token>()
        assertEquals(0, lexer.tokenize("still */ int b;", state, second))
        assertEquals(Tok.COMMENT, typeAt(second, 2))
    }

    @Test
    fun completionRanksPrefixAndFileWords() {
        val kotlin = Languages.byId("kotlin")!!
        val text = "val myCounter = 1\nmyC"
        val index = WordIndexer.build(text, kotlin, 1)
        val r = CompletionEngine.complete(CompletionRequest(text, text.length, kotlin, null, index, false, null))
        assertNotNull(r)
        assertEquals("myCounter", r!!.items.first().label)
        assertEquals(text.length - 3, r.replaceStart)
    }

    @Test
    fun kotlinSnippetExpands() {
        val kotlin = Languages.byId("kotlin")!!
        val fn = kotlin.vocabulary.snippets.first { it.trigger == "fun" }
        val exp = SnippetExpander.expand(fn.body, "    ", "    ")
        assertTrue(exp.text, exp.text.startsWith("fun name(): Unit {\n        \n    }"))
        assertEquals("name", exp.text.substring(exp.stops[0][0].first, exp.stops[0][0].last + 1))
    }

    @Test
    fun manifestAttributeCompletion() {
        val xml = Languages.byId("xml")!!
        val text = "<manifest>\n  <activity android:exp"
        val r = CompletionEngine.complete(CompletionRequest(text, text.length, xml, null, null, false, null))
        assertNotNull(r)
        assertTrue(r!!.items.any { it.label == "android:exported" && it.kind == ItemKind.ATTRIBUTE })
    }

    @Test
    fun detectsByNameAndContent() {
        assertEquals("kotlin", Languages.detect("build.gradle.kts").id)
        assertEquals("smali", Languages.detect("A.smali").id)
        assertEquals("shell", Languages.detect("run", "#!/system/bin/sh\necho").id)
        assertEquals("xml", Languages.detect("noext", "<?xml version=\"1.0\"?><a/>").id)
    }
}
