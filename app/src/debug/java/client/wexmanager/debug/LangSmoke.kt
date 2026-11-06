package client.wexmanager.debug

import client.wexmanager.feature.editor.lang.CompletionEngine
import client.wexmanager.feature.editor.lang.CompletionRequest
import client.wexmanager.feature.editor.lang.Languages
import client.wexmanager.feature.editor.lang.SnippetExpander
import client.wexmanager.feature.editor.lang.Token
import client.wexmanager.feature.editor.lang.WordIndexer

/**
 * Прогон всех языков редактора: лексеры, словари, шаблоны, индекс слов и подсказки на смеси
 * синтаксисов. Нужен потому, что регулярные выражения на Android (ICU) строже, чем на JVM, —
 * юнит-тест ловит ошибки логики, а запуск на устройстве — несовместимые шаблоны:
 * `adb shell CLASSPATH=<base.apk> app_process / client.wexmanager.debug.LangSmoke`.
 */
object LangSmoke {
    @JvmStatic
    fun main(args: Array<String>) {
        val errors = run()
        println(if (errors.isEmpty()) "LangSmoke OK: ${Languages.all.size} языков" else errors.joinToString("\n"))
    }

    fun run(): List<String> {
        val errors = ArrayList<String>()
        val text = SAMPLE
        // Позиции курсора: после каждого «интересного» символа и в концах слов.
        val carets = text.indices.filter { i -> i > 0 && (text[i - 1] in "<\"'./@$:-{[( =|>" || text[i - 1].isLetter() && !text[i].isLetterOrDigit()) }
        for (lang in Languages.all) {
            fun fail(stage: String, t: Throwable) {
                errors += "${lang.id} [$stage]: " + generateSequence(t) { it.cause?.takeIf { c -> c !== it } }.joinToString(" ← ")
            }
            try {
                var state = 0
                for (line in text.lines()) state = lang.lexer.tokenize(line, state, ArrayList<Token>())
            } catch (t: Throwable) { fail("лексер", t) }
            try {
                for (s in lang.vocabulary.snippets) SnippetExpander.expand(s.body, "    ", "    ")
            } catch (t: Throwable) { fail("шаблоны", t) }
            try {
                val snap = client.wexmanager.feature.editor.lang.HighlightSnapshot.build(text, lang)
                client.wexmanager.feature.editor.lang.OutlineEngine.compute(snap, client.wexmanager.feature.editor.lang.FoldingEngine.compute(snap))
            } catch (t: Throwable) { fail("структура", t) }
            val index = try { WordIndexer.build(text, lang, 1) } catch (t: Throwable) { fail("индекс", t); null }
            for (caret in carets) {
                for (explicit in listOf(false, true)) {
                    try {
                        CompletionEngine.complete(CompletionRequest(text, caret, lang, null, index, explicit, null))
                    } catch (t: Throwable) {
                        fail("подсказки @$caret", t)
                        break
                    }
                }
            }
        }
        for (name in listOf("a.kt", "build.gradle.kts", "x.smali", "AndroidManifest.xml", "Makefile", "a.h", "x.inc", "config", "noext")) {
            try { Languages.detect(name, text) } catch (t: Throwable) { errors += "detect $name: $t" }
        }
        return errors
    }

    private val SAMPLE = """
        #!/usr/bin/env bash
        # comment ${'$'}HOME "${'$'}{USER}" `date` ${'$'}(ls /system/bin) && echo 'q' | grep -E "a{2,3}" > /dev/null
        package demo; import java.util.*; @Deprecated("x") fun main(args: Array<String>) { val s = "a${'$'}{1 + 2}b\n"; /* c */ }
        /** doc @param x */ class A<T> : B(), C { override fun f(x: Int = 0x1F, y: Double = 1.5e-3f) = x shl 2 }
        val re = /ab+c/gi; const t = `tpl ${'$'}{x}`; let o = { "k": [1, 2.0, true, null] };
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="a.b">
          <application android:label="@string/app" android:theme="?attr/x" android:enabled="true">
            <activity android:name=".Main" android:exported="false"/><!-- c --> <![CDATA[ x ]]> &amp;
          </application>
        </manifest>
        <TextView android:layout_width="
        .class public Lcom/a/B;
        .method public static f(ILjava/lang/String;)V
            .locals 2
            const-string v0, "s"
            invoke-virtual {p0, v1}, Lcom/a/B;->g(I)Ljava/lang/Object;
            move-result-object v0
            if-eqz v0, :cond_0
            :cond_0
            return-void
        .end method
        def f(x: int) -> str:
            return f"{x!r}" if x else r'\d+' # py
        SELECT a, b FROM t WHERE c = 'x''y' -- sql
        .btn:hover { color: #fff; margin: 0 auto; --main: var(--x); } @media (max-width: 10px) { a { b: c } }
        # Заголовок
        **жирный** *курсив* `код` [ссылка](http://x) > цитата
        - пункт
        ```kotlin
        fun x() = 1
        ```
        diff --git a/x b/x
        @@ -1,2 +1,3 @@
        +add
        -del
        [section]
        key = value ; c
        key2: "yaml" # c
        a,b,"c,d",e
        ALOAD_0 INVOKESPECIAL java/lang/Object.<init> ()V
        L0 LINENUMBER 5 L0
        mov %eax, %ebx ; asm
        %define X 1
        \begin{document} ${'$'}x^2${'$'} \end{document}
        <?php echo ${'$'}x; ?>
        {{ template }} {% tag %}
        (defn f [x] (+ x 1)) ; lisp
        end
    """.trimIndent()
}
