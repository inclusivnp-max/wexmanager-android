package client.wexmanager.feature.wave100

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Ищет U+200B, U+200C, U+200D, U+FEFF и U+202A–U+202E и показывает позиции с 1. */
@Composable
fun T46ZeroWidthScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var text by rememberSaveable { mutableStateOf("") }
    val hits = findInvisibleMarks(text)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Невидимые знаки") },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.surface,
                    scrolledContainerColor = scheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Ищет пробелы нулевой ширины и метки направления: " +
                    "U+200B, U+200C, U+200D, U+FEFF, U+202A–U+202E. " +
                    "Позиция считается с 1 по символам UTF-16.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Текст") },
                placeholder = { Text("Вставьте или введите текст") },
                trailingIcon = if (text.isEmpty()) {
                    null
                } else {
                    {
                        IconButton(onClick = { text = "" }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.Close, "Очистить")
                        }
                    }
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                minLines = 6,
                maxLines = 12,
            )
            Text(
                if (hits.isEmpty()) "Не найдено" else "Найдено: ${hits.size}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (hits.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = scheme.surfaceContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (hit in hits) {
                            Text(
                                "${hit.index} — ${hit.code}, ${hit.name}" +
                                    " (стр. ${hit.line}, стлб. ${hit.column})",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                            )
                        }
                    }
                }
            }
            Button(
                onClick = { text = stripInvisibleMarks(text) },
                enabled = hits.isNotEmpty(),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text("Удалить")
            }
        }
    }
}

private data class InvisibleHit(
    val index: Int,
    val line: Int,
    val column: Int,
    val code: String,
    val name: String,
)

private val INVISIBLE_MARKS: Map<Char, String> = mapOf(
    '\u200B' to "пробел нулевой ширины",
    '\u200C' to "несоединитель нулевой ширины",
    '\u200D' to "соединитель нулевой ширины",
    '\uFEFF' to "неразрывный пробел нулевой ширины",
    '\u202A' to "вложение слева направо",
    '\u202B' to "вложение справа налево",
    '\u202C' to "конец направленности",
    '\u202D' to "переопределение слева направо",
    '\u202E' to "переопределение справа налево",
)

private fun findInvisibleMarks(text: String): List<InvisibleHit> {
    if (text.isEmpty()) return emptyList()
    val hits = ArrayList<InvisibleHit>()
    var line = 1
    var column = 1
    for (i in text.indices) {
        val ch = text[i]
        val name = INVISIBLE_MARKS[ch]
        if (name != null) {
            hits.add(
                InvisibleHit(
                    index = i + 1,
                    line = line,
                    column = column,
                    code = "U+%04X".format(ch.code),
                    name = name,
                ),
            )
        }
        if (ch == '\n') {
            line++
            column = 1
        } else {
            column++
        }
    }
    return hits
}

private fun stripInvisibleMarks(text: String): String {
    if (text.isEmpty()) return text
    return buildString(text.length) {
        for (ch in text) {
            if (ch !in INVISIBLE_MARKS) append(ch)
        }
    }
}
