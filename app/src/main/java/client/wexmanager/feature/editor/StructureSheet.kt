package client.wexmanager.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import client.wexmanager.feature.editor.lang.FoldingEngine
import client.wexmanager.feature.editor.lang.HighlightSnapshot
import client.wexmanager.feature.editor.lang.ItemKind
import client.wexmanager.feature.editor.lang.Language
import client.wexmanager.feature.editor.lang.OutlineEngine
import client.wexmanager.feature.editor.lang.OutlineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

/** Структура файла (классы, функции, теги, ключи…) — как «Структура» в WEXdesktop; касание — переход к строке. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StructureSheet(text: () -> String, language: Language, currentLine: Int, onJump: (Int) -> Unit, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var items by remember { mutableStateOf<List<OutlineItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val t = text()
        val r = runCatching {
            runInterruptible(Dispatchers.Default) {
                val snap = HighlightSnapshot.build(t, language)
                OutlineEngine.compute(snap, FoldingEngine.compute(snap))
            }
        }
        error = r.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }
        items = r.getOrDefault(emptyList())
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AccountTree, null, tint = scheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("Структура", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                items?.let { Text("${it.size}", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant) }
            }
            Spacer(Modifier.size(12.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text("Фильтр") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(8.dp))
            val list = items
            val needle = query.trim()
            val shown = list?.filter { needle.isBlank() || it.name.contains(needle, ignoreCase = true) || it.detail.contains(needle, ignoreCase = true) }
            if (shown != null) {
                Text(
                    "Найдено: ${shown.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
                Spacer(Modifier.size(4.dp))
            }
            when {
                list == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                list.isEmpty() -> Text(
                    error?.let { "Не удалось построить структуру: $it" } ?: "Для этого языка структура не определена или в файле нет объявлений",
                    Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center, color = scheme.onSurfaceVariant,
                )
                else -> {
                    val rows = shown.orEmpty()
                    // Текущий элемент — последний, что начинается не ниже курсора.
                    val current = list.lastOrNull { it.line <= currentLine }
                    LazyColumn(Modifier.heightIn(max = 560.dp), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(rows, key = { "${it.line}:${it.name}:${it.depth}" }) { item ->
                            val selected = item === current
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (selected) scheme.secondaryContainer else Color.Transparent)
                                    .clickable { onJump(item.line); onDismiss() }
                                    .padding(start = (12 + 16 * item.depth.coerceAtMost(8)).dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                KindBadge(item.kind)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (item.detail.isNotBlank()) Text(
                                        item.detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text("${item.line + 1}", style = MaterialTheme.typography.labelSmall, color = scheme.outline)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KindBadge(kind: ItemKind) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (kind) {
        ItemKind.TYPE, ItemKind.MODULE -> scheme.primaryContainer to scheme.onPrimaryContainer
        ItemKind.FUNCTION, ItemKind.OPCODE -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        ItemKind.PROPERTY, ItemKind.VARIABLE, ItemKind.CONSTANT -> scheme.secondaryContainer to scheme.onSecondaryContainer
        else -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
    }
    Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(bg), contentAlignment = Alignment.Center) {
        Text(kind.badge, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}
