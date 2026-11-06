package client.wexmanager.feature.browser

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.IconButton
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import client.wexmanager.ui.components.PressIconButton

data class ToolbarActions(
    val onUp: () -> Unit,
    val onPathJump: () -> Unit,
    val onHistory: () -> Unit,
    val onHistoryBack: () -> Unit,
    val onSync: () -> Unit,
    val onFilter: () -> Unit,
    val onBookmarks: () -> Unit,
    val onBookmarkHere: () -> Unit,
    val onCreate: () -> Unit,
    val onCloseSelection: () -> Unit,
    val onCopyOther: () -> Unit,
    val onMoveOther: () -> Unit,
    val onCopyClipboard: () -> Unit,
    val onCutClipboard: () -> Unit,
    val onDelete: () -> Unit,
    val onRename: () -> Unit,
    val onMore: () -> Unit,
    val onGo: () -> Unit = {},
)

@Composable
fun BrowserToolbar(
    selectionMode: Boolean,
    selectionCount: Int,
    canGoUp: Boolean,
    canGoBack: Boolean,
    actions: ToolbarActions,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = selectionMode,
        transitionSpec = {
            (scaleIn(motion.fastSpatialSpec(), initialScale = 0.85f) + fadeIn(motion.fastEffectsSpec()))
                .togetherWith(scaleOut(motion.fastSpatialSpec(), targetScale = 0.85f) + fadeOut(motion.fastEffectsSpec()))
        },
        contentAlignment = Alignment.Center,
        modifier = modifier,
        label = "toolbar",
    ) { selecting ->
        if (!selecting) {
            HorizontalFloatingToolbar(
                expanded = true,
                floatingActionButton = {
                    FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = actions.onCreate) {
                        Icon(Icons.Rounded.Add, "Создать")
                    }
                },
                colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
            ) {
                PressIconButton(Icons.Rounded.ArrowUpward, "Вверх (долгое нажатие — перейти к пути)", actions.onUp, onLongClick = actions.onPathJump, enabled = canGoUp)
                PressIconButton(Icons.Rounded.History, "Назад по истории (долгое — история)", actions.onHistoryBack, onLongClick = actions.onHistory, enabled = canGoBack)
                PressIconButton(Icons.Rounded.SyncAlt, "Синхронизировать панели (долгое — фильтр)", actions.onSync, onLongClick = actions.onFilter)
                PressIconButton(Icons.Rounded.Bookmarks, "Закладки (долгое — добавить)", actions.onBookmarks, onLongClick = actions.onBookmarkHere)
                IconButton(onClick = actions.onGo, modifier = Modifier.semantics { contentDescription = "GO · рабочее пространство" }) {
                    Text("GO", style = MaterialTheme.typography.labelLarge)
                }
            }
        } else {
            HorizontalFloatingToolbar(
                expanded = true,
                colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                leadingContent = {
                    PressIconButton(Icons.Rounded.Close, "Снять выделение", actions.onCloseSelection)
                },
            ) {
                Box(Modifier.align(Alignment.CenterVertically).widthIn(min = 20.dp), contentAlignment = Alignment.Center) {
                    AnimatedContent(selectionCount, label = "count") { n ->
                        Text(
                            "$n",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                PressIconButton(Icons.Rounded.ContentCopy, "Копировать в соседнюю панель (долгое — в буфер)", actions.onCopyOther, onLongClick = actions.onCopyClipboard)
                PressIconButton(Icons.AutoMirrored.Rounded.DriveFileMove, "Переместить в соседнюю панель (долгое — вырезать)", actions.onMoveOther, onLongClick = actions.onCutClipboard)
                PressIconButton(Icons.Rounded.DriveFileRenameOutline, "Переименовать", actions.onRename)
                PressIconButton(Icons.Rounded.Delete, "Удалить", actions.onDelete)
                PressIconButton(Icons.Rounded.MoreVert, "Ещё", actions.onMore)
            }
        }
    }
}
