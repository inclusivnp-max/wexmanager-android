package client.wexmanager.core.settings

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class Bookmark(
    val path: String,
    val title: String,
    val isDirectory: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

@Immutable
@Serializable
data class RecentEntry(
    val path: String,
    val openedAt: Long,
    val isDirectory: Boolean,
)

/** Пользовательская иконка для расширения: глиф из встроенного набора с цветом или картинка. */
@Immutable
@Serializable
data class IconOverride(
    val glyph: String? = null,
    val color: Int? = null,
    val imagePath: String? = null,
)

/** Импортированный ключ подписи APK (сам ключ — в Android Keystore под [alias]). */
@Immutable
@Serializable
data class SigningKeyMeta(
    val alias: String,
    val name: String,
    val subject: String,
    val sha256: String,
    val notAfter: Long,
    val algorithm: String,
    val addedAt: Long = System.currentTimeMillis(),
)

@Immutable
@Serializable
data class UserData(
    val bookmarks: List<Bookmark> = emptyList(),
    val recents: List<RecentEntry> = emptyList(),
    val iconOverrides: Map<String, IconOverride> = emptyMap(),
    /** Цветные метки отдельных папок: путь → ARGB. */
    val folderColors: Map<String, Int> = emptyMap(),
    /** Последняя позиция курсора в редакторе: путь → строка (до 100 файлов). */
    val editorPositions: Map<String, Int> = emptyMap(),
    val leftPath: String? = null,
    /** Домашние папки панелей («Сделать домашней» как в MT). */
    val homeLeft: String? = null,
    val homeRight: String? = null,
    val rightPath: String? = null,
    val activePanel: Int = 0,
    val searchHistory: List<String> = emptyList(),
    /** «Частые пароли» для архивов (хранятся в приватной папке приложения). */
    val archivePasswords: List<String> = emptyList(),
    val pathHistory: List<String> = emptyList(),
    /** «Всегда открывать так»: расширение → режим OpenAs (имя константы). */
    val openWith: Map<String, String> = emptyMap(),
    val signingKeys: List<SigningKeyMeta> = emptyList(),
    /** Alias активного ключа подписи; null — встроенный ключ WexManager. */
    val activeSigningKey: String? = null,
)
