package client.wexmanager.feature.editor

import client.wexmanager.feature.editor.lang.FoldingEngine
import client.wexmanager.feature.editor.lang.HighlightSnapshot
import client.wexmanager.feature.editor.lang.ItemKind
import client.wexmanager.feature.editor.lang.Languages
import client.wexmanager.feature.editor.lang.OutlineEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutlineTest {
    private fun outline(name: String, text: String) =
        HighlightSnapshot.build(text, Languages.detect(name, text)).let { OutlineEngine.compute(it, FoldingEngine.compute(it)) }

    @Test
    fun kotlinClassesAndFunctions() {
        val items = outline("A.kt", """
            class Foo {
                // fun notThis()
                fun bar(x: Int) = x
                val s = "fun fake()"
            }
            fun top() {}
        """.trimIndent())
        assertEquals(listOf("Foo", "bar", "s", "top"), items.map { it.name })
        assertEquals(ItemKind.TYPE, items[0].kind)
        assertEquals(1, items[1].depth)
        assertEquals(2, items[1].line)
    }

    @Test
    fun smaliMethodsAndFields() {
        val items = outline("A.smali", """
            .class public Lcom/a/B;
            .super Ljava/lang/Object;
            .field private x:I
            .method public run()V
                return-void
            .end method
        """.trimIndent())
        assertTrue(items.map { it.name }.toString(), items.any { it.name.contains("run") })
        assertTrue(items.map { it.name }.toString(), items.any { it.name.contains("x") })
    }
}
