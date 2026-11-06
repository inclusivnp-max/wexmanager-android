package client.wexmanager.feature.editor

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import client.wexmanager.core.settings.EditorThemeOption
import com.materialkolor.hct.Hct
import com.materialkolor.ktx.toColor
import com.materialkolor.ktx.toHct
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.eclipse.tm4e.core.registry.IThemeSource
import java.util.Locale

/** Ленивая одноразовая инициализация тем TextMate (рамка редактора: фон, номера строк, выделение). */
object TextMateSetup {
    @Volatile private var initialized = false
    private val loadedThemes = HashSet<String>()

    fun ensure(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            // Только темы: подсветку делают лексеры WEXdesktop (грамматики TextMate не нужны).
            FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(context.applicationContext.assets))
            initialized = true
        }
    }

    private fun bundledFile(option: EditorThemeOption): Pair<String, Boolean>? = when (option) {
        EditorThemeOption.MaterialYou -> null
        EditorThemeOption.Darcula -> "textmate/theme_darcula.json" to true
        EditorThemeOption.Monokai -> "textmate/theme_monokai.json" to true
        EditorThemeOption.QuietLight -> "textmate/theme_quietlight.json" to false
        EditorThemeOption.SolarizedDark -> "textmate/theme_solarized_dark.json" to true
        EditorThemeOption.AyuDark -> "textmate/theme_ayu-dark.json" to true
    }

    /** Применяет тему к реестру и возвращает признак тёмной темы. */
    @Synchronized
    fun applyTheme(option: EditorThemeOption, scheme: ColorScheme, dark: Boolean): Boolean {
        val registry = ThemeRegistry.getInstance()
        val bundled = bundledFile(option)
        if (bundled != null) {
            val (path, isDark) = bundled
            if (path !in loadedThemes) {
                val stream = FileProviderRegistry.getInstance().tryGetInputStream(path)
                val model = ThemeModel(IThemeSource.fromInputStream(stream, path, null), path)
                model.isDark = isDark
                registry.loadTheme(model)
                loadedThemes += path
            }
            registry.setTheme(path)
            return isDark
        }
        val json = materialThemeJson(scheme, dark)
        val name = "material-you-${json.hashCode()}"
        // Повторно не грузим: одинаковая палитра даёт одинаковое имя (промежуточные кадры анимации отсекает задержка на экране).
        if (name !in loadedThemes) {
            val model = ThemeModel(IThemeSource.fromString(IThemeSource.ContentType.JSON, json), name)
            model.isDark = dark
            registry.loadTheme(model)
            loadedThemes += name
        }
        registry.setTheme(name)
        return dark
    }

    private fun hex(c: Color): String = String.format(Locale.US, "#%06X", c.toArgb() and 0xFFFFFF)

    /** Синтаксические цвета — повороты оттенка от primary в пространстве HCT (гармонично с темой). */
    private fun materialThemeJson(scheme: ColorScheme, dark: Boolean): String {
        val base = scheme.primary.toHct()
        val tone = if (dark) 80.0 else 40.0
        fun rot(deg: Double, chroma: Double = 56.0, t: Double = tone): String =
            hex(Hct.from((base.hue + deg + 360) % 360, chroma, t).toColor())
        val keyword = hex(scheme.primary)
        val string = rot(120.0)
        val number = rot(60.0, 60.0)
        val function = rot(210.0, 48.0)
        val type = rot(300.0, 46.0)
        val constant = rot(30.0, 64.0)
        val tag = rot(-30.0, 60.0)
        val attr = rot(150.0, 44.0)
        val comment = hex(scheme.outline)
        val fg = hex(scheme.onSurface)
        val punct = hex(scheme.onSurfaceVariant)
        val inserted = hex(Hct.from(140.0, 60.0, tone).toColor())
        val deleted = hex(Hct.from(25.0, 70.0, tone).toColor())
        fun rule(scopes: String, color: String, style: String? = null) =
            """{"scope":[$scopes],"settings":{"foreground":"$color"${if (style != null) ",\"fontStyle\":\"$style\"" else ""}}}"""
        val rules = listOf(
            rule("\"comment\",\"punctuation.definition.comment\",\"string.comment\"", comment, "italic"),
            rule("\"string\",\"string.quoted\",\"string.template\",\"punctuation.definition.string\"", string),
            rule("\"constant.character.escape\",\"constant.other.placeholder\"", constant),
            rule("\"constant.numeric\",\"constant.language.boolean\",\"keyword.other.unit\"", number),
            rule("\"constant.language\",\"constant.other\",\"variable.other.constant\",\"variable.other.enummember\"", constant),
            rule("\"keyword\",\"storage\",\"storage.type\",\"storage.modifier\",\"keyword.control\",\"keyword.other\"", keyword, "bold"),
            rule("\"keyword.operator\",\"keyword.operator.arrow\"", punct),
            rule("\"entity.name.function\",\"support.function\",\"meta.function-call.generic\",\"variable.function\"", function),
            rule("\"entity.name.type\",\"entity.name.class\",\"support.class\",\"support.type\",\"entity.other.inherited-class\",\"storage.type.java\",\"storage.type.primitive\"", type),
            rule("\"variable.parameter\",\"variable.parameter.register.smali\"", attr),
            rule("\"variable\",\"variable.other\",\"meta.definition.variable\"", fg),
            rule("\"entity.name.tag\",\"meta.tag.sgml\",\"punctuation.definition.tag\"", tag),
            rule("\"entity.other.attribute-name\",\"support.type.property-name\",\"meta.object-literal.key\"", attr),
            rule("\"storage.type.annotation\",\"meta.annotation\",\"punctuation.definition.annotation\",\"entity.name.function.decorator\"", rot(90.0, 50.0)),
            rule("\"entity.name.tag.label.smali\",\"entity.name.label\"", constant),
            rule("\"punctuation\",\"meta.brace\",\"punctuation.separator\",\"punctuation.terminator\"", punct),
            rule("\"markup.heading\",\"entity.name.section\"", keyword, "bold"),
            rule("\"markup.bold\"", fg, "bold"),
            rule("\"markup.italic\"", fg, "italic"),
            rule("\"markup.inline.raw\",\"markup.fenced_code\",\"markup.raw\"", string),
            rule("\"markup.underline.link\",\"string.other.link\"", function, "underline"),
            rule("\"markup.inserted\",\"meta.diff.header.to-file\"", inserted),
            rule("\"markup.deleted\",\"meta.diff.header.from-file\"", deleted),
            rule("\"markup.changed\",\"meta.diff.range\"", number),
            rule("\"invalid\"", hex(scheme.error), "underline"),
        )
        return """{"name":"material-you","type":"${if (dark) "dark" else "light"}",
            "colors":{"editor.background":"${hex(scheme.surface)}","editor.foreground":"$fg"},
            "tokenColors":[${rules.joinToString(",")}]}"""
    }

    /** Подгоняет «рамочные» цвета редактора под Material 3 (для темы Material You — полностью). */
    fun styleScheme(cs: EditorColorScheme, scheme: ColorScheme, materialYou: Boolean) {
        fun set(id: Int, c: Color) = cs.setColor(id, c.toArgb())
        if (materialYou) {
            set(EditorColorScheme.WHOLE_BACKGROUND, scheme.surface)
            set(EditorColorScheme.LINE_NUMBER_BACKGROUND, scheme.surface)
            set(EditorColorScheme.TEXT_NORMAL, scheme.onSurface)
            set(EditorColorScheme.LINE_NUMBER, scheme.outline)
            set(EditorColorScheme.LINE_NUMBER_CURRENT, scheme.primary)
            set(EditorColorScheme.CURRENT_LINE, scheme.surfaceContainerHigh)
            set(EditorColorScheme.LINE_DIVIDER, Color.Transparent)
            set(EditorColorScheme.SELECTED_TEXT_BACKGROUND, scheme.primary.copy(alpha = 0.28f))
            set(EditorColorScheme.BLOCK_LINE, scheme.outlineVariant)
            set(EditorColorScheme.BLOCK_LINE_CURRENT, scheme.primary.copy(alpha = 0.7f))
            set(EditorColorScheme.SIDE_BLOCK_LINE, scheme.outlineVariant)
            set(EditorColorScheme.NON_PRINTABLE_CHAR, scheme.outlineVariant)
            set(EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND, scheme.primary)
            set(EditorColorScheme.HIGHLIGHTED_DELIMITERS_UNDERLINE, scheme.primary)
            set(EditorColorScheme.HIGHLIGHTED_DELIMITERS_BACKGROUND, scheme.primary.copy(alpha = 0.22f))
            set(EditorColorScheme.STICKY_SCROLL_DIVIDER, scheme.outlineVariant)
        }
        // Капля с номером строки у ползунка — как в списках файлов, в любой теме.
        set(EditorColorScheme.LINE_NUMBER_PANEL, scheme.primaryContainer)
        set(EditorColorScheme.LINE_NUMBER_PANEL_TEXT, scheme.onPrimaryContainer)
        set(EditorColorScheme.SELECTION_INSERT, scheme.primary)
        set(EditorColorScheme.SELECTION_HANDLE, scheme.primary)
        set(EditorColorScheme.MATCHED_TEXT_BACKGROUND, scheme.tertiary.copy(alpha = 0.35f))
        set(EditorColorScheme.SCROLL_BAR_THUMB, scheme.outline.copy(alpha = 0.5f))
        set(EditorColorScheme.SCROLL_BAR_THUMB_PRESSED, scheme.primary)
        set(EditorColorScheme.SCROLL_BAR_TRACK, Color.Transparent)
        set(EditorColorScheme.COMPLETION_WND_BACKGROUND, scheme.surfaceContainerHigh)
        set(EditorColorScheme.COMPLETION_WND_CORNER, scheme.outlineVariant)
        set(EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY, scheme.onSurface)
        set(EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY, scheme.onSurfaceVariant)
        set(EditorColorScheme.COMPLETION_WND_TEXT_MATCHED, scheme.primary)
        set(EditorColorScheme.COMPLETION_WND_ITEM_CURRENT, scheme.secondaryContainer)
        set(EditorColorScheme.TEXT_ACTION_WINDOW_BACKGROUND, scheme.surfaceContainerHighest)
        set(EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR, scheme.onSurface)
    }

    fun newColorScheme(): TextMateColorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())

    /**
     * Схема TextMate подписывается на глобальный реестр тем и сама не отписывается: без этого
     * каждое открытие файла и смена палитры оставляли в реестре слушателя, и смена темы
     * перекрашивала всё больше мёртвых схем.
     */
    fun release(scheme: io.github.rosemoe.sora.widget.schemes.EditorColorScheme?) {
        (scheme as? TextMateColorScheme)?.let { ThemeRegistry.getInstance().removeListener(it) }
    }
}
