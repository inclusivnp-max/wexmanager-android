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

private val OpenToClose = mapOf(
    '(' to ')',
    '[' to ']',
    '{' to '}',
)

/**
 * Первое несоответствие (), [] и {}.
 * Лишняя или чужая закрывающая — её индекс.
 * Незакрытые открывающие — индекс самой левой из них.
 * null, если пары сходятся. Индекс с нуля, по UTF-16.
 */
private fun firstBracketMismatch(text: String): Int? {
    val stack = ArrayDeque<Int>()
    text.forEachIndexed { index, ch ->
        when (ch) {
            '(' -> stack.addLast(index)
            ')' -> if (closeMismatch(stack, text, ch)) return index
            '[' -> stack.addLast(index)
            ']' -> if (closeMismatch(stack, text, ch)) return index
            '{' -> stack.addLast(index)
            '}' -> if (closeMismatch(stack, text, ch)) return index
        }
    }
    return stack.firstOrNull()
}

/** true, если закрывающая лишняя или не пара к вершине стека. */
private fun closeMismatch(stack: ArrayDeque<Int>, text: String, ch: Char): Boolean {
    val openIndex = stack.removeLastOrNull() ?: return true
    return OpenToClose[text[openIndex]] != ch
}

/** Проверка баланса круглых, квадратных и фигурных скобок. Сеть не используется. */
@Composable
fun T98BracketsScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var text by rememberSaveable { mutableStateOf("") }
    val mismatch = firstBracketMismatch(text)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Баланс скобок") },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
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
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Учитываются только (), [] и {}. Остальные символы пропускаются. " +
                    "Если пары не сходятся, показывается индекс первого сбоя, с нуля.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Текст") },
                placeholder = { Text("{ a: [1, (2)] }", fontFamily = FontFamily.Monospace) },
                minLines = 6,
                maxLines = 14,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = if (mismatch == null) scheme.surfaceContainer else scheme.errorContainer,
            ) {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        if (mismatch == null) "Сбалансировано" else "Несоответствие",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (mismatch == null) scheme.onSurface else scheme.onErrorContainer,
                    )
                    Text(
                        if (mismatch == null) {
                            "Первого несоответствия нет"
                        } else {
                            "Первое несоответствие: индекс $mismatch («${text[mismatch]}»)"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (mismatch == null) scheme.onSurfaceVariant else scheme.onErrorContainer,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}
