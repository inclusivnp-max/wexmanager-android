package client.wexmanager.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey {
    @Serializable data object Onboarding : Route
    @Serializable data object Browser : Route
    @Serializable data object GoWorkspace : Route

    /** [archive]/[entry] — файл открыт из архива (редактируется копия во временной папке). */
    @Serializable data class Editor(
        val path: String,
        val archive: String? = null,
        val entry: String? = null,
        val readOnly: Boolean = false,
        /** content:// источника: правки локальной копии записываются обратно. */
        val sourceUri: String? = null,
        /** Кодировка, если известна заранее (после «Сохранить как»), — без повторного определения. */
        val charset: String? = null,
        /** Режим «только чтение» нельзя снять (копия из архива, который мы не умеем перепаковать). */
        val lockReadOnly: Boolean = false,
        /** Исходный бинарный файл (AXML из APK или JVM .class): [path] — его текстовая расшифровка, при сохранении собираем обратно. */
        val binarySource: String? = null,
    ) : Route
    @Serializable data class ImageViewer(val path: String, val siblings: Boolean = true) : Route
    @Serializable data class PdfViewer(val path: String) : Route
    @Serializable data class FontViewer(val path: String) : Route
    @Serializable data class MediaPlayer(val path: String) : Route
    /** [readOnly] — копия файла из архива: правки всё равно не попали бы в архив. */
    @Serializable data class HexViewer(val path: String, val readOnly: Boolean = false) : Route
    @Serializable data class ApkInfo(val path: String) : Route
    /** DEX-редактор: [path] — `.dex` или APK; [entries] — DEX внутри APK (пусто — сам файл). */
    @Serializable data class DexEditor(val path: String, val entries: List<String> = emptyList()) : Route
    /** Таблица ресурсов: APK (resources.arsc внутри) или отдельный .arsc. */
    @Serializable data class ArscEditor(val path: String) : Route
    @Serializable data class DatabaseViewer(val path: String) : Route
    @Serializable data class SplitInstall(val path: String) : Route
    @Serializable data class ElfViewer(val path: String) : Route
    /** Предпросмотр Markdown/HTML/CSV; [base] — папка для относительных ссылок (если файл — временная копия). */
    @Serializable data class Preview(val path: String, val base: String? = null) : Route
    @Serializable data class Search(val root: String) : Route
    @Serializable data class Analyzer(val root: String) : Route
    @Serializable data object Apps : Route
    @Serializable data object Trash : Route
    @Serializable data object Tasks : Route
    @Serializable data class Terminal(val cwd: String) : Route
    @Serializable data object Network : Route
    @Serializable data object SmaliReference : Route
    @Serializable data object Logcat : Route
    @Serializable data object ShareServer : Route
    @Serializable data object Components : Route
    @Serializable data object Extras : Route
    @Serializable data object WaveHub : Route
    @Serializable data class Duplicates(val root: String) : Route
    @Serializable data class Cleanup(val root: String) : Route
    @Serializable data class Diff(val left: String, val right: String) : Route
    @Serializable data class FolderCompare(val left: String, val right: String) : Route
    @Serializable data class RemoteBrowser(val connectionId: String) : Route

    @Serializable data object Settings : Route
    @Serializable data object SettingsAppearance : Route
    @Serializable data object SettingsFileList : Route
    @Serializable data object SettingsBehavior : Route
    @Serializable data object SettingsEditor : Route
    @Serializable data object SettingsIcons : Route
    @Serializable data object SettingsBackup : Route
    @Serializable data object SettingsSigning : Route
    @Serializable data object About : Route
}
