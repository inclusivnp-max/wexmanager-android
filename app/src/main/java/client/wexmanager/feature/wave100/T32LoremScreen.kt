package client.wexmanager.feature.wave100

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import kotlinx.coroutines.launch

private const val MaxLines = 400

private val Heads = arrayOf(
    "зир", "мол", "тав", "крен", "лут", "сяб", "нор", "пел",
    "вил", "гра", "шек", "рик", "чаб", "оль", "бус", "кен",
    "лав", "ток", "фен", "яр", "сул", "реб", "шик", "дом",
    "пыр", "гол", "цев", "май", "нур", "тел", "хва", "зор",
)

private val Tails = arrayOf(
    "ка", "ен", "ис", "ок", "аль", "ут", "ик", "ань",
    "оль", "ец", "арь", "ин", "ыш", "ун", "ей", "ом",
    "ак", "ир", "ус", "ёв",
)

private val Glue = arrayOf("и", "на", "у", "без", "для", "от", "под", "к", "при", "над")

/** N строк из коротких выдуманных слов. Не чужой текст-заполнитель. */
@Composable
fun T32LoremScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val scroll = rememberScrollState()
    var countText by rememberSaveable { mutableStateOf("12") }
    var lines by rememberSaveable { mutableStateOf("") }

    fun tell(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    fun copy() {
        val copied = lines.isNotEmpty() && copyPlain(context, "Заполнитель", lines)
        tell(
            when {
                lines.isEmpty() -> "Нечего копировать"
                copied -> "Скопировано"
                else -> "Не удалось скопировать"
            },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Заполнитель") },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = ::copy, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.ContentCopy, "Копировать")
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Сколько строк нейтрального текста собрать из коротких выдуманных слов.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = countText,
                onValueChange = { countText = it.filter(Char::isDigit).take(4) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Число строк") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        val n = countText.toIntOrNull()
                        if (n == null || n < 1) {
                            tell("Нужно целое число от 1")
                        } else {
                            val taken = n.coerceAtMost(MaxLines)
                            lines = placeholderLines(taken)
                            if (taken != n) tell("Слишком много, взято $MaxLines")
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Сгенерировать") }
                FilledTonalButton(
                    onClick = ::copy,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Копировать")
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll),
            ) {
                Text(
                    text = lines.ifEmpty { "Здесь появятся строки. Нажмите «Сгенерировать»." },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (lines.isEmpty()) scheme.onSurfaceVariant else scheme.onSurface,
                )
            }
        }
    }
}

private fun placeholderLines(count: Int, random: Random = Random.Default): String = buildString {
    repeat(count) { index ->
        if (index > 0) append('\n')
        append(placeholderLine(random))
    }
}

private fun placeholderLine(random: Random): String {
    val words = random.nextInt(4, 9)
    return buildString {
        repeat(words) { index ->
            if (index > 0) append(' ')
            val word = if (index > 0 && random.nextInt(4) == 0) {
                Glue[random.nextInt(Glue.size)]
            } else {
                inventedWord(random)
            }
            append(if (index == 0) word.replaceFirstChar { it.titlecaseChar() } else word)
        }
        append('.')
    }
}

private fun inventedWord(random: Random): String =
    Heads[random.nextInt(Heads.size)] + Tails[random.nextInt(Tails.size)]

private fun copyPlain(context: Context, label: String, value: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
    manager.setPrimaryClip(ClipData.newPlainText(label, value))
    true
} catch (_: RuntimeException) {
    false
}
