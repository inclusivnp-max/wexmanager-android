package client.wexmanager.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    content: LazyListScope.() -> Unit,
) {
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(behavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title) },
                subtitle = subtitle?.let { { Text(it) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
                scrollBehavior = behavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = 32.dp),
        ) {
            content()
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

class SettingsGroupScope {
    internal val items = mutableListOf<@Composable (ListItemShapes) -> Unit>()

    fun custom(content: @Composable (ListItemShapes) -> Unit) {
        items += content
    }

    fun switch(
        title: String,
        checked: Boolean,
        icon: ImageVector? = null,
        supporting: String? = null,
        enabled: Boolean = true,
        onChange: (Boolean) -> Unit,
    ) = custom { shapes ->
        SegmentedListItem(
            onClick = { onChange(!checked) },
            shapes = shapes,
            enabled = enabled,
            leadingContent = icon?.let { { Icon(it, null) } },
            supportingContent = supporting?.let { { Text(it) } },
            trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        ) { Text(title) }
    }

    fun click(
        title: String,
        icon: ImageVector? = null,
        supporting: String? = null,
        trailing: (@Composable () -> Unit)? = null,
        enabled: Boolean = true,
        onClick: () -> Unit,
    ) = custom { shapes ->
        SegmentedListItem(
            onClick = onClick,
            shapes = shapes,
            enabled = enabled,
            leadingContent = icon?.let { { Icon(it, null) } },
            supportingContent = supporting?.let { { Text(it) } },
            trailingContent = trailing,
        ) { Text(title) }
    }

    fun <T> choice(
        title: String,
        options: List<T>,
        selected: T,
        label: (T) -> String,
        icon: ImageVector? = null,
        description: ((T) -> String?)? = null,
        onSelect: (T) -> Unit,
    ) = custom { shapes ->
        var open by remember { mutableStateOf(false) }
        SegmentedListItem(
            onClick = { open = true },
            shapes = shapes,
            leadingContent = icon?.let { { Icon(it, null) } },
            supportingContent = { Text(label(selected), color = MaterialTheme.colorScheme.primary) },
        ) { Text(title) }
        if (open) ChoiceDialog(title, options, selected, label, description, onDismiss = { open = false }) {
            onSelect(it)
            open = false
        }
    }

    fun slider(
        title: String,
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        steps: Int = 0,
        icon: ImageVector? = null,
        valueLabel: (Float) -> String,
        onChange: (Float) -> Unit,
    ) = custom { shapes ->
        var local by remember(value) { mutableFloatStateOf(value) }
        SegmentedListItem(
            onClick = {},
            shapes = shapes,
            leadingContent = icon?.let { { Icon(it, null) } },
            supportingContent = {
                Slider(
                    value = local,
                    onValueChange = { local = it },
                    onValueChangeFinished = { onChange(local) },
                    valueRange = range,
                    steps = steps,
                )
            },
            trailingContent = { Text(valueLabel(local), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) },
        ) { Text(title) }
    }
}

fun LazyListScope.settingsGroup(title: String?, key: String = title ?: "group", build: SettingsGroupScope.() -> Unit) {
    item(key = key) {
        val scope = SettingsGroupScope().apply(build)
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                scope.items.forEachIndexed { i, content ->
                    content(ListItemDefaults.segmentedShapes(i, scope.items.size))
                }
            }
        }
    }
}

@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    description: ((T) -> String?)? = null,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                options.forEachIndexed { i, o ->
                    SegmentedListItem(
                        selected = o == selected,
                        onClick = { onSelect(o) },
                        shapes = ListItemDefaults.segmentedShapes(i, options.size),
                        supportingContent = description?.invoke(o)?.let { { Text(it) } },
                        trailingContent = if (o == selected) ({ Icon(Icons.Rounded.Check, null) }) else null,
                    ) { Text(label(o)) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

@Composable
fun SettingsHint(text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(8.dp))
}
