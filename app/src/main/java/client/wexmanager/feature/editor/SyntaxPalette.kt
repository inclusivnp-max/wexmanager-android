package client.wexmanager.feature.editor

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import client.wexmanager.core.settings.EditorThemeOption
import client.wexmanager.feature.editor.lang.ItemKind
import client.wexmanager.feature.editor.lang.Tok
import com.materialkolor.hct.Hct
import com.materialkolor.ktx.toColor
import com.materialkolor.ktx.toHct
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.json.JSONArray
import org.json.JSONObject

/**
 * Цвета токенов подсветки. У каждого [Tok] свой номер цвета в схеме Sora (после встроенных, до 255 —
 * там начинаются цвета TextMate), поэтому смена темы — это только новые значения в схеме,
 * без повторного разбора текста.
 */
object SyntaxPalette {
    private const val TOK_BASE = 150

    fun colorId(t: Tok): Int = TOK_BASE + t.ordinal

    private val styles = LongArray(Tok.entries.size) { i ->
        val t = Tok.entries[i]
        if (t == Tok.TEXT || t == Tok.PUNCTUATION) TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)
        else TextStyle.makeStyle(colorId(t), 0, t in BOLD, t in ITALIC, false)
    }

    /** Стиль спана для токена (цвет, жирность, курсив). */
    fun style(t: Tok): Long = styles[t.ordinal]

    val normalStyle: Long = TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)

    private val BOLD get() = setOf(Tok.KEYWORD, Tok.CONTROL, Tok.SECTION, Tok.DIRECTIVE, Tok.HEADING, Tok.STRONG, Tok.HUNK)
    private val ITALIC get() = setOf(Tok.COMMENT, Tok.DOC_COMMENT, Tok.DOC_TAG, Tok.QUOTE, Tok.EMPHASIS)

    /** Значок вида подсказки окрашивается цветом подходящего токена — в тон теме. */
    fun kindColorId(kind: ItemKind): Int = colorId(
        when (kind) {
            ItemKind.KEYWORD -> Tok.KEYWORD
            ItemKind.TYPE -> Tok.TYPE
            ItemKind.FUNCTION, ItemKind.COMMAND -> Tok.FUNCTION
            ItemKind.PROPERTY, ItemKind.ATTRIBUTE -> Tok.ATTRIBUTE
            ItemKind.CONSTANT, ItemKind.VALUE -> Tok.CONSTANT
            ItemKind.SNIPPET -> Tok.ANNOTATION
            ItemKind.TAG -> Tok.TAG
            ItemKind.DIRECTIVE -> Tok.DIRECTIVE
            ItemKind.OPCODE -> Tok.OPCODE
            ItemKind.VARIABLE -> Tok.VARIABLE
            ItemKind.LABEL -> Tok.LABEL
            ItemKind.FILE, ItemKind.MODULE -> Tok.STRING
            ItemKind.WORD -> Tok.COMMENT
        },
    )

    /** Заполняет цвета токенов в [cs] под выбранную тему. */
    fun apply(cs: EditorColorScheme, context: Context, option: EditorThemeOption, scheme: ColorScheme, dark: Boolean) {
        val colors: Map<Tok, Int> = when (val path = themeFile(option)) {
            null -> material(scheme, dark)
            else -> fromTheme(context, path, scheme)
        }
        val normal = cs.getColor(EditorColorScheme.TEXT_NORMAL)
        for (t in Tok.entries) cs.setColor(colorId(t), colors[t] ?: normal)
    }

    private fun themeFile(option: EditorThemeOption): String? = when (option) {
        EditorThemeOption.MaterialYou -> null
        EditorThemeOption.Darcula -> "textmate/theme_darcula.json"
        EditorThemeOption.Monokai -> "textmate/theme_monokai.json"
        EditorThemeOption.QuietLight -> "textmate/theme_quietlight.json"
        EditorThemeOption.SolarizedDark -> "textmate/theme_solarized_dark.json"
        EditorThemeOption.AyuDark -> "textmate/theme_ayu-dark.json"
    }

    // ---------- Material You: повороты оттенка от primary (как и рамка редактора) ----------

    private fun material(scheme: ColorScheme, dark: Boolean): Map<Tok, Int> {
        val base = scheme.primary.toHct()
        val tone = if (dark) 80.0 else 40.0
        fun rot(deg: Double, chroma: Double = 56.0): Int = Hct.from((base.hue + deg + 360) % 360, chroma, tone).toColor().toArgb()
        fun c(color: Color) = color.toArgb()
        val string = rot(120.0)
        val function = rot(210.0, 48.0)
        val number = rot(60.0, 60.0)
        val type = rot(300.0, 46.0)
        val variable = rot(-60.0, 48.0)
        return mapOf(
            Tok.KEYWORD to c(scheme.primary),
            Tok.CONTROL to rot(-45.0, 60.0),
            Tok.MODIFIER to c(scheme.primary),
            Tok.TYPE to type,
            Tok.BUILTIN to rot(240.0, 44.0),
            Tok.FUNCTION to function,
            Tok.CONSTANT to rot(30.0, 64.0),
            Tok.NUMBER to number,
            Tok.STRING to string,
            Tok.ESCAPE to rot(80.0, 60.0),
            Tok.REGEX to rot(165.0, 50.0),
            Tok.COMMENT to c(scheme.outline),
            Tok.DOC_COMMENT to rot(130.0, 20.0),
            Tok.DOC_TAG to rot(130.0, 34.0),
            Tok.ANNOTATION to rot(90.0, 50.0),
            Tok.PREPROCESSOR to c(scheme.tertiary),
            Tok.OPERATOR to c(scheme.onSurfaceVariant),
            Tok.BRACKET to c(scheme.onSurfaceVariant),
            Tok.TAG to rot(-30.0, 60.0),
            Tok.ATTRIBUTE to rot(150.0, 44.0),
            Tok.ENTITY to rot(80.0, 60.0),
            Tok.VARIABLE to variable,
            Tok.PROPERTY to rot(195.0, 44.0),
            Tok.SECTION to c(scheme.primary),
            Tok.LABEL to rot(120.0, 50.0),
            Tok.REGISTER to rot(180.0, 50.0),
            Tok.DIRECTIVE to c(scheme.primary),
            Tok.OPCODE to c(scheme.tertiary),
            Tok.HEADING to c(scheme.primary),
            Tok.EMPHASIS to c(scheme.onSurface),
            Tok.STRONG to c(scheme.onSurface),
            Tok.LINK to function,
            Tok.CODE to string,
            Tok.QUOTE to c(scheme.outline),
            Tok.LIST to c(scheme.secondary),
            Tok.INSERTED to Hct.from(140.0, 60.0, tone).toColor().toArgb(),
            Tok.DELETED to Hct.from(25.0, 70.0, tone).toColor().toArgb(),
            Tok.HUNK to function,
            Tok.META to c(scheme.tertiary),
            Tok.INVALID to c(scheme.error),
            Tok.COLUMN_0 to c(scheme.onSurface),
            Tok.COLUMN_1 to function,
            Tok.COLUMN_2 to string,
            Tok.COLUMN_3 to number,
            Tok.COLUMN_4 to type,
            Tok.COLUMN_5 to variable,
        )
    }

    // ---------- Темы TextMate / VS Code: цвет по самому точному совпадению скоупа ----------

    /** Скоупы-кандидаты по убыванию точности: первый, для которого в теме есть правило, задаёт цвет. */
    private val SCOPES: Map<Tok, List<String>> = mapOf(
        Tok.KEYWORD to listOf("keyword.other", "storage.type", "keyword", "storage"),
        Tok.CONTROL to listOf("keyword.control", "keyword"),
        Tok.MODIFIER to listOf("storage.modifier", "storage", "keyword"),
        Tok.TYPE to listOf("entity.name.type", "entity.name.class", "support.class", "support.type", "storage.type"),
        Tok.BUILTIN to listOf("support.function", "entity.name.function"),
        Tok.FUNCTION to listOf("entity.name.function", "support.function", "meta.function-call"),
        Tok.CONSTANT to listOf("constant.language", "constant", "variable.other.constant"),
        Tok.NUMBER to listOf("constant.numeric", "constant"),
        Tok.STRING to listOf("string.quoted", "string"),
        Tok.ESCAPE to listOf("constant.character.escape", "constant.character", "constant"),
        Tok.REGEX to listOf("string.regexp", "string"),
        Tok.COMMENT to listOf("comment.line", "comment"),
        Tok.DOC_COMMENT to listOf("comment.block.documentation", "comment"),
        Tok.DOC_TAG to listOf("storage.type.class.jsdoc", "keyword.other.documentation", "comment.block.documentation", "comment"),
        Tok.ANNOTATION to listOf("storage.type.annotation", "meta.annotation", "entity.name.function.decorator", "meta.decorator", "storage.type"),
        Tok.PREPROCESSOR to listOf("meta.preprocessor", "keyword.control.directive", "keyword.other.preprocessor", "keyword"),
        Tok.OPERATOR to listOf("keyword.operator"),
        Tok.BRACKET to listOf("punctuation.bracket", "meta.brace", "punctuation"),
        Tok.TAG to listOf("entity.name.tag"),
        Tok.ATTRIBUTE to listOf("entity.other.attribute-name"),
        Tok.ENTITY to listOf("constant.character.entity", "constant.character", "constant"),
        Tok.VARIABLE to listOf("variable.other.readwrite", "variable.other", "variable", "variable.language"),
        Tok.PROPERTY to listOf("support.type.property-name", "meta.object-literal.key", "variable.other.property", "entity.other.attribute-name"),
        Tok.SECTION to listOf("entity.name.section", "markup.heading", "entity.name.tag"),
        Tok.LABEL to listOf("entity.name.label", "entity.name.tag", "string"),
        Tok.REGISTER to listOf("variable.parameter", "variable"),
        Tok.DIRECTIVE to listOf("keyword.control.directive", "keyword.other", "keyword"),
        Tok.OPCODE to listOf("keyword.other", "support.function", "keyword"),
        Tok.HEADING to listOf("markup.heading", "entity.name.section", "keyword"),
        Tok.EMPHASIS to listOf("markup.italic"),
        Tok.STRONG to listOf("markup.bold"),
        Tok.LINK to listOf("markup.underline.link", "string.other.link", "markup.underline"),
        Tok.CODE to listOf("markup.inline.raw", "markup.raw", "markup.fenced_code", "string"),
        Tok.QUOTE to listOf("markup.quote", "comment"),
        Tok.LIST to listOf("markup.list", "punctuation.definition.list", "keyword"),
        Tok.INSERTED to listOf("markup.inserted"),
        Tok.DELETED to listOf("markup.deleted", "invalid"),
        Tok.HUNK to listOf("meta.diff.range", "meta.diff.header", "markup.changed", "entity.name.function"),
        Tok.META to listOf("meta.tag.metadata", "meta.preprocessor", "keyword.other", "comment"),
        Tok.INVALID to listOf("invalid.illegal", "invalid"),
    )

    private class Rule(val selector: String, val color: Int)

    private val parsed = HashMap<String, List<Rule>>()

    @Synchronized
    private fun rules(context: Context, path: String): List<Rule> = parsed.getOrPut(path) {
        val json = runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrNull() ?: return@getOrPut emptyList()
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return@getOrPut emptyList()
        val list = root.optJSONArray("tokenColors") ?: root.optJSONArray("settings") ?: JSONArray()
        val out = ArrayList<Rule>()
        for (i in 0 until list.length()) {
            val o = list.optJSONObject(i) ?: continue
            val fg = o.optJSONObject("settings")?.optString("foreground")?.let(::parseColor) ?: continue
            val selectors = when (val s = o.opt("scope")) {
                is String -> s.split(',')
                is JSONArray -> List(s.length()) { s.optString(it) }
                else -> emptyList()
            }
            for (sel in selectors) {
                val t = sel.trim()
                // Составные селекторы («meta.tag string») — для своих контекстов, здесь не подходят.
                if (t.isEmpty() || ' ' in t || t.startsWith("-")) continue
                out += Rule(t, fg)
            }
        }
        out
    }

    private fun fromTheme(context: Context, path: String, scheme: ColorScheme): Map<Tok, Int> {
        val rules = rules(context, path)
        fun resolve(scope: String): Int? {
            var best: Rule? = null
            for (r in rules) {
                val s = r.selector
                if ((scope == s || scope.startsWith("$s.")) && (best == null || s.length >= best.selector.length)) best = r
            }
            return best?.color
        }
        val out = HashMap<Tok, Int>()
        for ((tok, scopes) in SCOPES) {
            for (s in scopes) {
                val c = resolve(s) ?: continue
                out[tok] = c
                break
            }
        }
        if (Tok.INVALID !in out) out[Tok.INVALID] = scheme.error.toArgb()
        // Радужные колонки CSV/TSV — из уже найденных цветов темы.
        out[Tok.COLUMN_1] = out[Tok.FUNCTION] ?: out[Tok.KEYWORD] ?: 0
        out[Tok.COLUMN_2] = out[Tok.STRING] ?: 0
        out[Tok.COLUMN_3] = out[Tok.NUMBER] ?: 0
        out[Tok.COLUMN_4] = out[Tok.TYPE] ?: 0
        out[Tok.COLUMN_5] = out[Tok.VARIABLE] ?: out[Tok.CONSTANT] ?: 0
        return out.filterValues { it != 0 }
    }

    private fun parseColor(s: String): Int? {
        if (!s.startsWith("#")) return null
        val h = s.substring(1)
        val v = h.toLongOrNull(16) ?: return null
        return when (h.length) {
            3 -> {
                val r = (v shr 8 and 0xF) * 17
                val g = (v shr 4 and 0xF) * 17
                val b = (v and 0xF) * 17
                (0xFF000000 or (r shl 16) or (g shl 8) or b).toInt()
            }
            6 -> (0xFF000000 or v).toInt()
            8 -> ((v and 0xFF) shl 24 or (v ushr 8)).toInt()
            else -> null
        }
    }
}
