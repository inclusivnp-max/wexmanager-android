package client.wexmanager.feature.tools

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import client.wexmanager.appContainer
import client.wexmanager.core.ops.TaskState
import client.wexmanager.feature.browser.TaskCard

@Composable
fun TasksScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember(context) { context.appContainer.taskManager }
    val tasks by manager.tasks.collectAsStateWithLifecycle()
    val active = remember(tasks) { tasks.filter { it.isActive }.sortedByDescending { it.startedAt } }
    val finished = remember(tasks) { tasks.filterNot { it.isActive }.sortedByDescending { it.finishedAt } }
    var filter by remember { mutableStateOf(TaskFilter.All) }
    val showActive = filter != TaskFilter.Done
    val showFinished = filter != TaskFilter.Running
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme

    // Диалог конфликта показывает MainActivity поверх всех экранов.

    val subtitle = when {
        active.isNotEmpty() && finished.isNotEmpty() -> "Выполняется: ${active.size}  •  завершено: ${finished.size}"
        active.isNotEmpty() -> "Выполняется: ${active.size}"
        finished.isNotEmpty() -> "Завершено: ${finished.size}"
        else -> "Копирование, перемещение, архивы"
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Задачи") },
                subtitle = {
                    AnimatedContent(
                        targetState = subtitle,
                        transitionSpec = { fadeIn(motion.fastEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                        label = "tasksSubtitle",
                    ) { Text(it, maxLines = 1) }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.surface,
                    scrolledContainerColor = scheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = scheme.surface,
    ) { padding ->
        AnimatedContent(
            targetState = tasks.isEmpty(),
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
            label = "tasksContent",
        ) { empty ->
            if (empty) {
                ToolsEmptyState(
                    polygon = MaterialShapes.Clover8Leaf,
                    icon = Icons.Rounded.TaskAlt,
                    title = "Нет активных задач",
                    text = "Здесь появятся копирование, перемещение, удаление, сжатие и распаковка",
                    modifier = Modifier.padding(padding),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = padding.calculateTopPadding() + 4.dp,
                        bottom = padding.calculateBottomPadding() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "filter", contentType = "filter") {
                        TaskFilterChips(
                            selected = filter,
                            onSelect = { filter = it },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    if (showActive && active.isNotEmpty()) {
                        item(key = "header:active", contentType = "header") {
                            SectionHeader("Выполняются", modifier = Modifier.animateItem())
                        }
                        items(active, key = { it.id }, contentType = { "task" }) { task ->
                            TaskCard(
                                task = task,
                                onCancel = { manager.cancel(task.id) },
                                onDismiss = { manager.dismiss(task.id) },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = motion.defaultEffectsSpec(),
                                    placementSpec = motion.defaultSpatialSpec(),
                                    fadeOutSpec = motion.fastEffectsSpec(),
                                ),
                            )
                        }
                    }
                    if (showFinished && finished.isNotEmpty()) {
                        item(key = "header:finished", contentType = "header") {
                            SectionHeader(
                                "Завершённые",
                                modifier = Modifier.animateItem(),
                                action = {
                                    TextButton(onClick = { manager.dismissFinished() }, shapes = ButtonDefaults.shapes()) {
                                        Icon(Icons.Rounded.ClearAll, null, Modifier.size(ButtonDefaults.IconSize))
                                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                        Text("Очистить завершённые")
                                    }
                                },
                            )
                        }
                        items(finished, key = { it.id }, contentType = { "task" }) { task ->
                            DismissibleTask(
                                task = task,
                                onDismiss = { manager.dismiss(task.id) },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = motion.defaultEffectsSpec(),
                                    placementSpec = motion.defaultSpatialSpec(),
                                    fadeOutSpec = motion.fastEffectsSpec(),
                                ),
                            )
                        }
                    }
                    if ((!showActive || active.isEmpty()) && (!showFinished || finished.isEmpty())) {
                        item(key = "filter:empty", contentType = "empty") {
                            Text(
                                when (filter) {
                                    TaskFilter.Running -> "Нет выполняющихся задач"
                                    TaskFilter.Done -> "Нет завершённых задач"
                                    TaskFilter.All -> "Нет задач"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 24.dp).animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskFilterChips(selected: TaskFilter, onSelect: (TaskFilter) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TaskFilter.entries.forEach { item ->
            FilterChip(
                selected = item == selected,
                onClick = { onSelect(item) },
                label = { Text(item.label) },
            )
        }
    }
}

private enum class TaskFilter(val label: String) {
    All("Все"),
    Running("Идут"),
    Done("Готовые"),
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).padding(vertical = 10.dp),
        )
        action?.invoke()
    }
}

/** Завершённую задачу можно смахнуть в любую сторону. */
@Composable
private fun DismissibleTask(task: TaskState, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val state = rememberSwipeToDismissBoxState()
    val scheme = MaterialTheme.colorScheme
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        onDismiss = { onDismiss() },
        backgroundContent = {
            val direction = state.dismissDirection
            val bg by animateColorAsState(
                if (direction == SwipeToDismissBoxValue.Settled) Color.Transparent else scheme.secondaryContainer,
                MaterialTheme.motionScheme.fastEffectsSpec(),
                label = "dismissBg",
            )
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(bg).padding(horizontal = 24.dp),
                contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                if (direction != SwipeToDismissBoxValue.Settled) {
                    Icon(Icons.Rounded.ClearAll, "Скрыть", tint = scheme.onSecondaryContainer)
                }
            }
        },
    ) {
        TaskCard(task = task, onCancel = {}, onDismiss = onDismiss)
    }
}
