package client.wexmanager.core.settings

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode { System, Light, Dark }

@Serializable
enum class PaletteStyleOption { TonalSpot, Vibrant, Expressive, Fidelity, Content, Rainbow, FruitSalad, Neutral, Monochrome }

@Serializable
enum class ViewMode { List, Grid }

@Serializable
enum class ListDensity { Compact, Comfortable, Spacious }

@Serializable
enum class SortBy { Name, Date, Size, Type }

@Serializable
enum class DateFormatOption { Relative, Short, Full }

@Serializable
enum class SizeUnits { Binary, Decimal }

@Serializable
enum class IconStyle { Tonal, Filled, Outlined, Mono }

@Serializable
enum class IconShapeOption { Squircle, Circle, Cookie, Clover, Rounded, Pill }

@Serializable
enum class DualPaneMode { Auto, Always, Single }

@Serializable
enum class EditorThemeOption { MaterialYou, Darcula, Monokai, QuietLight, SolarizedDark, AyuDark }

@Serializable
enum class EditorFont { JetBrainsMono, System }

@Serializable
enum class AnimationSpeed { Off, Fast, Normal, Relaxed }

@Immutable
@Serializable
data class AppSettings(
    // Внешний вид
    val themeMode: ThemeMode = ThemeMode.System,
    val amoled: Boolean = false,
    val dynamicColor: Boolean = true,
    val seedColor: Int = 0xFF4F6BED.toInt(),
    val paletteStyle: PaletteStyleOption = PaletteStyleOption.TonalSpot,
    val contrast: Float = 0f,
    val animationSpeed: AnimationSpeed = AnimationSpeed.Normal,

    // Список файлов
    val viewMode: ViewMode = ViewMode.List,
    val gridMinCellDp: Int = 104,
    val listDensity: ListDensity = ListDensity.Comfortable,
    val showHidden: Boolean = false,
    /** Root-доступ (только чтение) к закрытым папкам через su. */
    val rootAccess: Boolean = false,
    val foldersFirst: Boolean = true,
    val sortBy: SortBy = SortBy.Name,
    val sortDescending: Boolean = false,
    val showThumbnails: Boolean = true,
    val showFolderItemCount: Boolean = true,
    val showPermissions: Boolean = false,
    val showExtensions: Boolean = true,
    val dateFormat: DateFormatOption = DateFormatOption.Short,
    val sizeUnits: SizeUnits = SizeUnits.Binary,
    val fileNameMaxLines: Int = 1,
    val iconStyle: IconStyle = IconStyle.Tonal,
    val iconShape: IconShapeOption = IconShapeOption.Squircle,
    val fileListFontScale: Float = 1f,
    val showScrollbar: Boolean = true,

    // Поведение
    val dualPaneMode: DualPaneMode = DualPaneMode.Auto,
    val confirmDelete: Boolean = true,
    val useTrash: Boolean = true,
    val openArchivesAsFolders: Boolean = true,
    val rememberLastPaths: Boolean = true,
    val doubleBackToExit: Boolean = true,
    val haptics: Boolean = true,
    val autoRefresh: Boolean = true,
    val highlightReturnedFolder: Boolean = true,
    val tapIconToSelect: Boolean = true,
    val showStorageInfo: Boolean = true,

    // Редактор
    val editorFontSize: Float = 14f,
    val editorFont: EditorFont = EditorFont.JetBrainsMono,
    val editorWordWrap: Boolean = false,
    val editorLineNumbers: Boolean = true,
    val editorPinLineNumbers: Boolean = true,
    val editorHighlightLine: Boolean = true,
    val editorAutoIndent: Boolean = true,
    val editorTabSize: Int = 4,
    val editorUseSpaces: Boolean = true,
    val editorTheme: EditorThemeOption = EditorThemeOption.MaterialYou,
    val editorSymbolBar: Boolean = true,
    val editorShowWhitespace: Boolean = false,
    val editorAutoSave: Boolean = false,
    val editorDefaultEncoding: String = "UTF-8",
    val editorBracketPairs: Boolean = true,
    val editorStickyScroll: Boolean = true,
    /** Подсказки при наборе (как в WEXdesktop); по Ctrl+Space — всегда. */
    val editorAutoComplete: Boolean = true,

    // Иконки
    val activeIconPack: String? = null,
    val coloredFolderIcons: Boolean = true,

    // Служебное
    val onboardingDone: Boolean = false,
    val tipGesturesShown: Boolean = false,
)
