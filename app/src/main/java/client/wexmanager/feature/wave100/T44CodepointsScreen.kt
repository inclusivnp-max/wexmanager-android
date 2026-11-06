package client.wexmanager.feature.wave100

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import client.wexmanager.R
import kotlinx.coroutines.launch

private val Mono = FontFamily(Font(R.font.jetbrains_mono))
private const val MaxChars = 500

/** Кодовые точки Unicode: знак, U+XXXX и десятичное значение. Не больше 500 символов. */
@Composable
fun T44CodepointsScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var text by rememberSaveable { mutableStateOf("") }
    val capped = remember(text) { text.take(MaxChars) }
    val rows = remember(capped) { codePointRows(capped) }
    val listing = remember(rows) { rows.joinToString("\n") { "${it.glyph}  ${it.hex}  ${it.decimal}" } }

    fun copyListing() {
        val copied = listing.isNotEmpty() && copyPlain(context, listing)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                when {
                    listing.isEmpty() -> "Нечего копировать"
                    copied -> "Скопировано"
                    else -> "Не удалось скопировать"
                },
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Кодовые точки") },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { copyListing() },
                        enabled = rows.isNotEmpty(),
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.Rounded.ContentCopy, "Копировать")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.surface,
                    scrolledContainerColor = scheme.surfaceContainer,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Для каждой кодовой точки — знак, код U+XXXX и десятичное значение. " +
                    "Не больше $MaxChars символов.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = capped,
                onValueChange = { text = it.take(MaxChars) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Текст") },
                placeholder = { Text("Введите или вставьте текст") },
                supportingText = { Text("${capped.length} / $MaxChars") },
                minLines = 3,
                maxLines = 6,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    countLabel(rows.size, capped.length),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(
                    onClick = { copyListing() },
                    enabled = rows.isNotEmpty(),
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Копировать")
                }
            }
            if (rows.isEmpty()) {
                Text(
                    "Нет символов",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(rows, key = { index, _ -> index }) { _, row ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = scheme.surfaceContainer,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    row.glyph,
                                    modifier = Modifier.width(40.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = Mono,
                                    color = scheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                )
                                Text(
                                    row.hex,
                                    modifier = Modifier.width(104.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontFamily = Mono,
                                    color = scheme.onSurface,
                                    maxLines = 1,
                                )
                                Text(
                                    row.decimal,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontFamily = Mono,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class CodePointRow(val glyph: String, val hex: String, val decimal: String)

private fun codePointRows(text: String): List<CodePointRow> {
    if (text.isEmpty()) return emptyList()
    val rows = ArrayList<CodePointRow>(text.length)
    var index = 0
    while (index < text.length) {
        val cp = text.codePointAt(index)
        rows.add(
            CodePointRow(
                glyph = displayChar(cp),
                hex = "U+%04X".format(cp),
                decimal = cp.toString(),
            ),
        )
        index += Character.charCount(cp)
    }
    return rows
}

/** Печатный знак. Переводы строк и управляющие не ломают строку списка. */
private fun displayChar(cp: Int): String = when (cp) {
    '\n'.code, 0x85, 0x2028, 0x2029 -> "↵"
    '\r'.code -> "␍"
    '\t'.code -> "⇥"
    ' '.code -> "␣"
    0x00A0 -> "⍽"
    else -> {
        val type = Character.getType(cp)
        when (type) {
            Character.CONTROL.toInt(), Character.SURROGATE.toInt() -> "�"
            Character.FORMAT.toInt() -> "◌"
            else -> String(Character.toChars(cp))
        }
    }
}

private fun countLabel(points: Int, chars: Int): String {
    val pointsWord = plural(points, "кодовая точка", "кодовые точки", "кодовых точек")
    return if (points == chars) {
        "$points $pointsWord"
    } else {
        val charsWord = plural(chars, "символ", "символа", "символов")
        "$points $pointsWord · $chars $charsWord"
    }
}

private fun plural(count: Int, one: String, few: String, many: String): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}

private fun copyPlain(context: Context, value: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
    manager.setPrimaryClip(ClipData.newPlainText("Кодовые точки", value))
    true
} catch (_: RuntimeException) {
    false
}
