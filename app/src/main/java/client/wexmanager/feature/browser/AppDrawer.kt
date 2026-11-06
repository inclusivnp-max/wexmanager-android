package client.wexmanager.feature.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.DarkMode
import client.wexmanager.appContainer
import client.wexmanager.core.settings.ThemeMode
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import client.wexmanager.core.fs.StorageInfo
import client.wexmanager.core.fs.StorageRepository
import client.wexmanager.core.settings.Bookmark
import client.wexmanager.core.settings.RecentEntry
import client.wexmanager.core.util.Format
import client.wexmanager.ui.components.PolygonShape
import client.wexmanager.ui.theme.LocalAppSettings
import java.io.File

sealed interface DrawerDestination {
    data class Path(val path: String) : DrawerDestination
    data class File(val path: String) : DrawerDestination
    data object Search : DrawerDestination
    data object Analyzer : DrawerDestination
    data object Apps : DrawerDestination
    data object Trash : DrawerDestination
    data object Tasks : DrawerDestination
    data object Terminal : DrawerDestination
    data object Duplicates : DrawerDestination
    data object Cleanup : DrawerDestination
    data object Network : DrawerDestination
    data object Smali : DrawerDestination
    data object Logcat : DrawerDestination
    data object ShareServer : DrawerDestination
    data object Components : DrawerDestination
    data object Extras : DrawerDestination
    data object WaveHub : DrawerDestination
    data object Settings : DrawerDestination
    data object About : DrawerDestination
}

private fun quickIcon(kind: String): ImageVector = when (kind) {
    "download" -> Icons.Rounded.Download
    "camera" -> Icons.Rounded.PhotoCamera
    "image" -> Icons.Rounded.Image
    "document" -> Icons.Rounded.Description
    "music" -> Icons.Rounded.LibraryMusic
    "video" -> Icons.Rounded.VideoLibrary
    "android" -> Icons.Rounded.Android
    else -> Icons.Rounded.Folder
}

@Composable
fun AppDrawerContent(
    storage: StorageRepository,
    volumes: List<StorageInfo>,
    bookmarks: List<Bookmark>,
    recents: List<RecentEntry>,
    currentPath: String?,
    activeTasks: Int,
    permanent: Boolean = false,
    onDestination: (DrawerDestination) -> Unit,
) {
    val s = LocalAppSettings.current
    val scheme = MaterialTheme.colorScheme
    val sheet: @Composable (@Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) -> Unit = { body ->
        if (permanent) androidx.compose.material3.PermanentDrawerSheet(
            modifier = Modifier.width(300.dp),
            drawerContainerColor = scheme.surfaceContainerLow,
            content = body,
        ) else ModalDrawerSheet(drawerContainerColor = scheme.surfaceContainerLow, content = body)
    }
    sheet {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            Row(Modifier.padding(start = 12.dp, top = 20.dp, bottom = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val shape = remember { PolygonShape(MaterialShapes.Cookie9Sided) }
                Box(Modifier.size(48.dp).clip(shape).background(scheme.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Folder, null, tint = scheme.onPrimary)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("WexManager", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Файловый менеджер", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                // Быстрая смена темы: авто → светлая → тёмная.
                val context = androidx.compose.ui.platform.LocalContext.current
                androidx.compose.material3.IconButton(
                    onClick = {
                        context.appContainer.settings.update {
                            it.copy(themeMode = when (it.themeMode) {
                                ThemeMode.System -> ThemeMode.Light
                                ThemeMode.Light -> ThemeMode.Dark
                                ThemeMode.Dark -> ThemeMode.System
                            })
                        }
                    },
                    shapes = androidx.compose.material3.IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        when (s.themeMode) {
                            ThemeMode.System -> Icons.Rounded.BrightnessAuto
                            ThemeMode.Light -> Icons.Rounded.LightMode
                            ThemeMode.Dark -> Icons.Rounded.DarkMode
                        },
                        "Сменить тему",
                    )
                }
            }

            DrawerLabel("Хранилища")
            volumes.forEach { v ->
                NavigationDrawerItem(
                    label = {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(v.title, style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { v.usedFraction },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = if (v.usedFraction > 0.9f) scheme.error else scheme.primary,
                                trackColor = scheme.surfaceContainerHighest,
                                gapSize = 2.dp,
                                drawStopIndicator = {},
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "${Format.size(v.free, s.sizeUnits)} свободно из ${Format.size(v.total, s.sizeUnits)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    },
                    icon = { Icon(if (v.isPrimary) Icons.Rounded.PhoneAndroid else Icons.Rounded.SdCard, null) },
                    selected = currentPath == v.path,
                    onClick = { onDestination(DrawerDestination.Path(v.path)) },
                )
            }
            NavigationDrawerItem(
                label = { Text("Корень системы  /") },
                icon = { Icon(Icons.Rounded.Memory, null) },
                selected = currentPath == "/",
                onClick = { onDestination(DrawerDestination.Path("/")) },
            )

            val quick by produceState(emptyList<StorageRepository.QuickAccess>()) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { storage.quickAccess() }
            }
            if (quick.isNotEmpty()) {
                DrawerLabel("Быстрый доступ")
                quick.forEach { q ->
                    NavigationDrawerItem(
                        label = { Text(q.title) },
                        icon = { Icon(quickIcon(q.kind), null) },
                        selected = currentPath == q.path,
                        onClick = { onDestination(DrawerDestination.Path(q.path)) },
                    )
                }
            }

            if (bookmarks.isNotEmpty()) {
                DrawerLabel("Закладки")
                bookmarks.take(10).forEach { b ->
                    NavigationDrawerItem(
                        label = { Text(b.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        icon = { Icon(Icons.Rounded.Bookmark, null) },
                        selected = currentPath == b.path,
                        onClick = { onDestination(if (b.isDirectory) DrawerDestination.Path(b.path) else DrawerDestination.File(b.path)) },
                    )
                }
            }

            val recentFiles by produceState(emptyList<RecentEntry>(), recents) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    recents.asSequence().filter { !it.isDirectory && File(it.path).exists() }.take(5).toList()
                }
            }
            if (recentFiles.isNotEmpty()) {
                DrawerLabel("Недавние файлы")
                recentFiles.forEach { r ->
                    NavigationDrawerItem(
                        label = { Text(r.path.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
                        icon = { Icon(Icons.Rounded.Schedule, null) },
                        selected = false,
                        onClick = { onDestination(DrawerDestination.File(r.path)) },
                    )
                }
            }

            DrawerLabel("Инструменты")
            NavigationDrawerItem(label = { Text("Поиск файлов") }, icon = { Icon(Icons.Rounded.Search, null) }, selected = false, onClick = { onDestination(DrawerDestination.Search) })
            NavigationDrawerItem(label = { Text("Анализ хранилища") }, icon = { Icon(Icons.Rounded.DonutLarge, null) }, selected = false, onClick = { onDestination(DrawerDestination.Analyzer) })
            NavigationDrawerItem(label = { Text("Приложения") }, icon = { Icon(Icons.Rounded.Apps, null) }, selected = false, onClick = { onDestination(DrawerDestination.Apps) })
            NavigationDrawerItem(label = { Text("Сеть: SFTP, FTP, SMB, WebDAV") }, icon = { Icon(Icons.Rounded.Lan, null) }, selected = false, onClick = { onDestination(DrawerDestination.Network) })
            NavigationDrawerItem(label = { Text("Очистка памяти") }, icon = { Icon(Icons.Rounded.CleaningServices, null) }, selected = false, onClick = { onDestination(DrawerDestination.Cleanup) })
            NavigationDrawerItem(label = { Text("Поиск дубликатов") }, icon = { Icon(Icons.Rounded.FileCopy, null) }, selected = false, onClick = { onDestination(DrawerDestination.Duplicates) })
            NavigationDrawerItem(label = { Text("Справочник Smali") }, icon = { Icon(Icons.AutoMirrored.Rounded.MenuBook, null) }, selected = false, onClick = { onDestination(DrawerDestination.Smali) })
            NavigationDrawerItem(label = { Text("Терминал") }, icon = { Icon(Icons.Rounded.Terminal, null) }, selected = false, onClick = { onDestination(DrawerDestination.Terminal) })
            NavigationDrawerItem(label = { Text("Журнал logcat") }, icon = { Icon(Icons.Rounded.BugReport, null) }, selected = false, onClick = { onDestination(DrawerDestination.Logcat) })
            NavigationDrawerItem(label = { Text("Раздача по HTTP") }, icon = { Icon(Icons.Rounded.Share, null) }, selected = false, onClick = { onDestination(DrawerDestination.ShareServer) })
            NavigationDrawerItem(label = { Text("Компоненты приложений") }, icon = { Icon(Icons.Rounded.ToggleOn, null) }, selected = false, onClick = { onDestination(DrawerDestination.Components) })
            NavigationDrawerItem(label = { Text("Мелочи") }, icon = { Icon(Icons.Rounded.Handyman, null) }, selected = false, onClick = { onDestination(DrawerDestination.Extras) })
            NavigationDrawerItem(label = { Text("Мастерская") }, icon = { Icon(Icons.Rounded.Handyman, null) }, selected = false, onClick = { onDestination(DrawerDestination.WaveHub) })
            NavigationDrawerItem(label = { Text("Корзина") }, icon = { Icon(Icons.Rounded.Delete, null) }, selected = false, onClick = { onDestination(DrawerDestination.Trash) })
            NavigationDrawerItem(
                label = { Text("Задачи") },
                icon = { Icon(Icons.Rounded.TaskAlt, null) },
                badge = if (activeTasks > 0) ({ Text("$activeTasks") }) else null,
                selected = false,
                onClick = { onDestination(DrawerDestination.Tasks) },
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
            NavigationDrawerItem(label = { Text("Настройки") }, icon = { Icon(Icons.Rounded.Settings, null) }, selected = false, onClick = { onDestination(DrawerDestination.Settings) })
            NavigationDrawerItem(label = { Text("О приложении") }, icon = { Icon(Icons.Rounded.Info, null) }, selected = false, onClick = { onDestination(DrawerDestination.About) })
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DrawerLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
    )
}
