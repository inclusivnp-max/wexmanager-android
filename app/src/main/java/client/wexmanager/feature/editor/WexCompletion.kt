package client.wexmanager.feature.editor

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import client.wexmanager.feature.editor.lang.ItemKind
import client.wexmanager.feature.editor.lang.SnippetExpander
import io.github.rosemoe.sora.lang.completion.CompletionItemKind
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.component.EditorCompletionAdapter
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import client.wexmanager.feature.editor.lang.CompletionItem as EngineItem
import io.github.rosemoe.sora.lang.completion.CompletionItem as SoraItem

/** Подсказка движка десктопа в окне Sora: заменяет набранный префикс, шаблон разворачивается с полями. */
class WexCompletionItem(
    val item: EngineItem,
    prefixLen: Int,
    private val indentUnit: String,
) : SoraItem(item.label, item.detail) {
    init {
        prefixLength = prefixLen
        kind = when (item.kind) {
            ItemKind.KEYWORD -> CompletionItemKind.Keyword
            ItemKind.TYPE -> CompletionItemKind.Class
            ItemKind.FUNCTION -> CompletionItemKind.Function
            ItemKind.PROPERTY, ItemKind.ATTRIBUTE -> CompletionItemKind.Property
            ItemKind.CONSTANT -> CompletionItemKind.Constant
            ItemKind.VALUE -> CompletionItemKind.Value
            ItemKind.SNIPPET -> CompletionItemKind.Snippet
            ItemKind.VARIABLE, ItemKind.LABEL -> CompletionItemKind.Variable
            ItemKind.FILE -> CompletionItemKind.File
            ItemKind.MODULE -> CompletionItemKind.Module
            else -> CompletionItemKind.Text
        }
    }

    override fun performCompletion(editor: CodeEditor, text: Content, line: Int, column: Int) {
        val start = (column - prefixLength).coerceAtLeast(0)
        if (!item.isSnippet) {
            text.replace(line, start, line, column, item.insertText)
        } else {
            val l = text.getLine(line)
            var w = 0
            while (w < l.length && (l[w] == ' ' || l[w] == '\t')) w++
            val exp = SnippetExpander.expand(item.insertText, l.subSequence(0, w).toString(), indentUnit)
            text.replace(line, start, line, column, exp.text)
            val base = text.getCharIndex(line, start)
            val field = exp.stops.firstOrNull()?.firstOrNull()
            val idx = text.indexer
            if (field != null && !field.isEmpty()) {
                val a = idx.getCharPosition(base + field.first)
                val b = idx.getCharPosition(base + field.last + 1)
                editor.setSelectionRegion(a.line, a.column, b.line, b.column)
            } else {
                val p = idx.getCharPosition(base + (field?.first ?: exp.finalOffset))
                editor.setSelection(p.line, p.column)
            }
        }
        if (item.retrigger) {
            editor.post { editor.getComponent(EditorAutoCompletion::class.java).requireCompletion() }
        }
    }
}

/** Строка окна подсказок как в десктопе: значок вида, совпавшие буквы выделены, справа — пояснение. */
class WexCompletionAdapter(private val context: Context, private val typeface: () -> Typeface) : EditorCompletionAdapter() {
    private val density = context.resources.displayMetrics.density

    override fun getItemHeight(): Int = (40 * density).toInt()

    private class Holder(val badge: TextView, val label: TextView, val detail: TextView)

    override fun getView(pos: Int, view: View?, parent: ViewGroup?, isCurrentCursorPosition: Boolean): View {
        val row = (view as? LinearLayout) ?: create()
        val h = row.tag as Holder
        val it = getItem(pos)
        val cs = colorScheme
        val engine = (it as? WexCompletionItem)?.item
        val kind = engine?.kind ?: ItemKind.WORD
        val kindColor = cs.getColor(SyntaxPalette.kindColorId(kind))
        h.badge.text = kind.badge
        h.badge.setTextColor(kindColor)
        (h.badge.background as GradientDrawable).setColor(kindColor and 0x00FFFFFF or 0x2E000000)
        h.label.typeface = typeface()
        h.label.setTextColor(cs.getColor(EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY))
        h.label.text = highlighted(it.label.toString(), engine?.matched, cs.getColor(EditorColorScheme.COMPLETION_WND_TEXT_MATCHED))
        h.detail.text = it.desc ?: it.detail ?: ""
        h.detail.setTextColor(cs.getColor(EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY))
        h.detail.visibility = if (h.detail.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        row.setBackgroundColor(if (isCurrentCursorPosition) cs.getColor(EditorColorScheme.COMPLETION_WND_ITEM_CURRENT) else 0)
        return row
    }

    private fun highlighted(label: String, matched: IntArray?, color: Int): CharSequence {
        if (matched == null || matched.isEmpty()) return label
        val s = SpannableString(label)
        for (i in matched) {
            if (i !in label.indices) continue
            s.setSpan(ForegroundColorSpan(color), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(StyleSpan(Typeface.BOLD), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return s
    }

    private fun dp(v: Int) = (v * density).toInt()

    private fun create(): LinearLayout {
        val badge = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            background = GradientDrawable().apply { cornerRadius = 5 * density }
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(18)).apply { marginEnd = dp(10) }
        }
        val label = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val detail = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = dp(140)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(12) }
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(12), 0)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, itemHeight)
            addView(badge)
            addView(label)
            addView(detail)
            tag = Holder(badge, label, detail)
        }
    }
}
