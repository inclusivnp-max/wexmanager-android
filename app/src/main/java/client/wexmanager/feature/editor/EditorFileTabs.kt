package client.wexmanager.feature.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import client.wexmanager.feature.editor.golang.GoTooltip
import kotlinx.coroutines.flow.first

/** The rail is transparent; only each file control paints its own container. */
@Composable
internal fun EditorFileTabs(
    tabs: List<EditorTabs.Tab>,
    selectedId: Long,
    dirtyPaths: Set<String>,
    closeEnabled: Boolean,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    val list = rememberLazyListState()
    val ids = tabs.map { it.id }
    val motion = MaterialTheme.motionScheme
    LaunchedEffect(selectedId, ids) {
        val index = ids.indexOf(selectedId)
        if (index >= 0) {
            snapshotFlow { list.layoutInfo.totalItemsCount }.first { it == ids.size }
            list.animateScrollToItem(index)
        }
    }
    Row(modifier.fillMaxWidth().testTag("editor-file-tabs"), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(Modifier.weight(1f), state = list, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            items(tabs, key = { it.id }) { tab ->
                EditorFileTab(tab, selected = tab.id == selectedId, modified = tab.route.path in dirtyPaths,
                    closeEnabled = closeEnabled, onSelect = { onSelect(tab.id) }, onClose = { onClose(tab.id) },
                    modifier = Modifier.animateItem(fadeInSpec = motion.fastEffectsSpec(), fadeOutSpec = motion.fastEffectsSpec(), placementSpec = motion.defaultSpatialSpec()))
            }
        }
        actions()
    }
}

@Composable
private fun EditorFileTab(tab: EditorTabs.Tab, selected: Boolean, modified: Boolean, closeEnabled: Boolean,
    onSelect: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val fill by animateColorAsState(if (selected) scheme.primaryContainer else scheme.surfaceContainerLow, motion.fastEffectsSpec(), label = "file-tab-fill")
    val ink by animateColorAsState(if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant, motion.fastEffectsSpec(), label = "file-tab-ink")
    val outline by animateColorAsState(if (selected) scheme.primary.copy(alpha = 0.45f) else scheme.outlineVariant, motion.fastEffectsSpec(), label = "file-tab-outline")
    val colors = ButtonDefaults.filledTonalButtonColors(containerColor = fill, contentColor = ink,
        disabledContainerColor = fill, disabledContentColor = ink.copy(alpha = 0.38f))
    val description = tab.title + if (modified) ", есть несохранённые изменения" else ""
    SplitButtonLayout(modifier = modifier.semantics { isTraversalGroup = true }, leadingButton = {
        GoTooltip(tab.route.path) {
            SplitButtonDefaults.TonalLeadingButton(onClick = onSelect, colors = colors, border = BorderStroke(1.dp, outline),
                elevation = null, contentPadding = PaddingValues(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                modifier = Modifier.widthIn(min = 112.dp, max = 208.dp).heightIn(min = 48.dp).testTag("file-tab-${tab.id}")
                    .clearAndSetSemantics {
                        role = Role.Tab
                        this.selected = selected
                        contentDescription = description
                        onClick(label = "Открыть ${tab.title}") { onSelect(); true }
                    }) {
                Icon(if (tab.route.path.endsWith(".go") || tab.title in setOf("go.mod", "go.sum")) Icons.Rounded.Code else Icons.AutoMirrored.Rounded.InsertDriveFile, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tab.title, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
                if (modified) Box(Modifier.padding(start = 8.dp).size(6.dp).clip(MaterialTheme.shapes.extraLarge).background(ink).testTag("file-tab-dirty-${tab.id}"))
            }
        }
    }, trailingButton = {
        GoTooltip("Закрыть ${tab.title}") {
            SplitButtonDefaults.TrailingButton(onClick = onClose, enabled = closeEnabled, colors = colors, elevation = null,
                border = BorderStroke(1.dp, outline), modifier = Modifier.size(48.dp).testTag("file-tab-close-${tab.id}"),
                contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Rounded.Close, "Закрыть ${tab.title}", Modifier.size(18.dp))
            }
        }
    })
}
