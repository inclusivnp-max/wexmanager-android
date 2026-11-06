package client.wexmanager.feature.editor.golang

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.PauseCircleOutline
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Badge
import androidx.compose.material3.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal fun syntaxLabel(state: GoSyntaxState): String = when (state.phase) {
    GoSyntaxPhase.DISABLED -> "Автопроверка выключена"
    GoSyntaxPhase.PENDING, GoSyntaxPhase.CHECKING -> "Проверяем синтаксис…"
    GoSyntaxPhase.ERROR -> "Ошибка проверки синтаксиса"
    GoSyntaxPhase.READY -> if (state.problems.isEmpty()) "Синтаксис: OK" else "Ошибки: ${state.problems.size}"
}

@Composable
internal fun GoSyntaxButton(state: GoSyntaxState, onClick: () -> Unit) {
    val bad = state.problems.isNotEmpty() || state.phase == GoSyntaxPhase.ERROR
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val label = syntaxLabel(state)
    GoTooltip(label) {
        FilledTonalIconButton(onClick = onClick, shapes = IconButtonDefaults.shapes(), modifier = Modifier.semantics { contentDescription = label },
            colors = if (bad) IconButtonDefaults.filledTonalIconButtonColors(containerColor = scheme.errorContainer, contentColor = scheme.onErrorContainer)
                else IconButtonDefaults.filledTonalIconButtonColors()) {
            BadgedBox(badge = { if (state.problems.isNotEmpty()) Badge { Text(state.problems.size.toString()) } }) {
                AnimatedContent(targetState = state.phase to bad, label = "go-syntax-status", transitionSpec = {
                    fadeIn(motion.fastEffectsSpec()).togetherWith(fadeOut(motion.fastEffectsSpec()))
                }) { (phase, errors) ->
                    if (phase == GoSyntaxPhase.CHECKING || phase == GoSyntaxPhase.PENDING) LoadingIndicator(Modifier.size(24.dp))
                    else Icon(when { phase == GoSyntaxPhase.DISABLED -> Icons.Rounded.PauseCircleOutline; errors -> Icons.Rounded.ErrorOutline; else -> Icons.Rounded.CheckCircle }, null)
                }
            }
        }
    }
}

@Composable
internal fun GoFormatButton(enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    val motion = MaterialTheme.motionScheme
    GoTooltip(if (busy) "Форматируем…" else "Форматировать по gofmt") {
        FilledTonalIconButton(onClick = onClick, enabled = enabled || busy, shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.semantics { contentDescription = "Форматировать по gofmt" }) {
            AnimatedContent(targetState = busy, label = "go-format-state", transitionSpec = {
                (fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = 0.7f))
                    .togetherWith(fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.7f))
            }) { running ->
                if (running) LoadingIndicator(Modifier.size(24.dp))
                else Icon(Icons.Rounded.FormatAlignLeft, null)
            }
        }
    }
}

@Composable
internal fun GoProblemsSheet(state: GoSyntaxState, fileName: String, onDismiss: () -> Unit, onEnabled: (Boolean) -> Unit, onJump: (GoProblem) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.65f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Проблемы", style = MaterialTheme.typography.headlineSmall)
                    Text(fileName, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, style = MaterialTheme.typography.bodySmall)
                }
                GoIconButton(onClick = onDismiss, tonal = true, tooltip = "Закрыть список ошибок") { Icon(Icons.Rounded.Close, "Закрыть список ошибок") }
            }
            ListItem(trailingContent = { Switch(checked = state.enabled, onCheckedChange = onEnabled) },
                supportingContent = { Text(syntaxLabel(state)) }, shapes = ListItemDefaults.shapes()) { Text("Проверять при наборе") }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Text("Локальный Go-парсер · без сохранения и запуска. F8 / Shift+F8 — следующая / предыдущая ошибка. Проверка типов и импортов выполняется при сборке.", style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(state.problems, key = { _, problem -> "${problem.start}:${problem.end}:${problem.message}" }) { index, problem ->
                    ListItem(onClick = { onJump(problem); onDismiss() }, modifier = Modifier.fillMaxWidth(),
                        shapes = ListItemDefaults.segmentedShapes(index, state.problems.size),
                        supportingContent = { Text("Строка ${problem.line}, столбец ${problem.column}") },
                        leadingContent = { Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error) },
                    ) { Text(problem.message) }
                }
                if (state.phase == GoSyntaxPhase.READY && state.problems.isEmpty()) item {
                    ListItem(leadingContent = { Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
                        supportingContent = { Text("Можно продолжать работу") }) { Text("Синтаксических ошибок нет") }
                }
            }
        }
    }
}
