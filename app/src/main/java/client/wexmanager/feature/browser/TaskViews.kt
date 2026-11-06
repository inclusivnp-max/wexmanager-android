package client.wexmanager.feature.browser

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import client.wexmanager.core.ops.TaskState
import client.wexmanager.core.ops.TaskStatus
import client.wexmanager.core.util.Format
import client.wexmanager.ui.theme.LocalAppSettings

fun TaskState.detailText(units: client.wexmanager.core.settings.SizeUnits): String = when (status) {
    TaskStatus.Preparing -> "Подготовка…"
    TaskStatus.WaitingConflict -> "Ожидает решения"
    TaskStatus.Running -> buildString {
        if (totalBytes > 0) append("${Format.size(processedBytes, units)} из ${Format.size(totalBytes, units)}")
        else if (totalItems > 0) append("$processedItems из $totalItems")
        if (bytesPerSecond > 0) append("  •  ${Format.speed(bytesPerSecond)}")
        if (etaMillis > 0) append("  •  ещё ${Format.duration(etaMillis)}")
    }
    TaskStatus.Done -> "Готово" + if (totalItems > 0) " • ${Format.items(totalItems)}" else ""
    TaskStatus.Failed -> error ?: "Ошибка"
    TaskStatus.Cancelled -> "Отменено"
}

/** Первая непустая строка из списка сбоев, иначе из текста ошибки. */
private fun TaskState.failureLine(): String? {
    val fromItems = failedItems.firstOrNull()?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()
    if (!fromItems.isNullOrEmpty()) return fromItems
    return error?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.ifEmpty { null }
}

/** Компактная плашка над панелью инструментов с прогрессом активных задач. */
@Composable
fun TaskPill(tasks: List<TaskState>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val active = tasks.filter { it.isActive }
    val main = active.firstOrNull() ?: return
    val s = LocalAppSettings.current
    val progress by animateFloatAsState(main.progress, label = "taskProgress")
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
        modifier = modifier.clickable(onClick = onClick).animateContentSize(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (main.status == TaskStatus.Preparing) CircularWavyProgressIndicator(Modifier.size(32.dp))
            else CircularWavyProgressIndicator(progress = { progress }, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(main.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    main.detailText(s.sizeUnits) + if (active.size > 1) "  •  ещё ${active.size - 1}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun TaskCard(task: TaskState, onCancel: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val s = LocalAppSettings.current
    val scheme = MaterialTheme.colorScheme
    val progress by animateFloatAsState(task.progress, label = "p")
    Surface(shape = RoundedCornerShape(24.dp), color = scheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (task.status) {
                    TaskStatus.Done -> Icon(Icons.Rounded.CheckCircle, null, tint = scheme.primary)
                    TaskStatus.Failed -> Icon(Icons.Rounded.ErrorOutline, null, tint = scheme.error)
                    TaskStatus.Cancelled -> Icon(Icons.Rounded.Block, null, tint = scheme.outline)
                    else -> CircularWavyProgressIndicator(progress = { progress }, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    task.destination?.let {
                        Text("→ $it", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.StartEllipsis)
                    }
                }
                IconButton(onClick = if (task.isActive) onCancel else onDismiss) {
                    Icon(Icons.Rounded.Close, if (task.isActive) "Отменить" else "Скрыть")
                }
            }
            if (task.isActive) {
                Spacer(Modifier.height(10.dp))
                if (task.status == TaskStatus.Preparing) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                else LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                if (task.currentName.isNotEmpty()) {
                    Text(task.currentName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                }
            }
            val failed = task.status == TaskStatus.Failed
            val summary = task.detailText(s.sizeUnits)
            val failureLine = if (failed) task.failureLine() else null
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = if (failed) scheme.error else scheme.onSurfaceVariant,
                maxLines = if (failed) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
            )
            val line = failureLine?.takeIf { task.failedItems.isNotEmpty() && it != summary }
            if (line != null) {
                Text(line, style = MaterialTheme.typography.labelSmall, color = scheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val listed = if (line != null) task.failedItems.drop(1) else task.failedItems
            listed.take(5).forEach {
                Text("• $it", style = MaterialTheme.typography.labelSmall, color = scheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
