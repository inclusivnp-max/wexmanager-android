package client.wexmanager.feature.wave100

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Кириллица → латиница, простая схема в духе ГОСТ: ё=yo, ж=zh, й=y, х=kh, щ=sch. */
@Composable
fun T51TranslitScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var source by rememberSaveable { mutableStateOf("") }
    val latin = remember(source) { transliterateRu(source) }

    fun copyLatin() {
        val copied = latin.isNotEmpty() && copyPlain(context, latin)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                when {
                    latin.isEmpty() -> "Нечего копировать"
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
                title = { Text("Транслит") },
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
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Русский текст в латиницу. ё — yo, ж — zh, й — y, х — kh, ц — ts, " +
                    "ч — ch, ш — sh, щ — sch, ю — yu, я — ya. ъ и ь опускаются.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = source,
                onValueChange = { source = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Текст") },
                placeholder = { Text("Привет, щётка") },
                textStyle = MaterialTheme.typography.bodyLarge,
                minLines = 4,
                maxLines = 8,
            )
            OutlinedTextField(
                value = latin,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                label = { Text("Латиница") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                minLines = 4,
                maxLines = 8,
            )
            FilledTonalButton(
                onClick = ::copyLatin,
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Копировать")
            }
        }
    }
}

private val RU_LATIN = mapOf(
    'а' to "a",
    'б' to "b",
    'в' to "v",
    'г' to "g",
    'д' to "d",
    'е' to "e",
    'ё' to "yo",
    'ж' to "zh",
    'з' to "z",
    'и' to "i",
    'й' to "y",
    'к' to "k",
    'л' to "l",
    'м' to "m",
    'н' to "n",
    'о' to "o",
    'п' to "p",
    'р' to "r",
    'с' to "s",
    'т' to "t",
    'у' to "u",
    'ф' to "f",
    'х' to "kh",
    'ц' to "ts",
    'ч' to "ch",
    'ш' to "sh",
    'щ' to "sch",
    'ъ' to "",
    'ы' to "y",
    'ь' to "",
    'э' to "e",
    'ю' to "yu",
    'я' to "ya",
)

/** Посимвольно. Заглавная в ряду заглавных пишется целиком заглавными: ЖАР → ZHAR, Жар → Zhar. */
private fun transliterateRu(text: String): String = buildString(text.length * 2) {
    var index = 0
    while (index < text.length) {
        val ch = text[index]
        val mapped = RU_LATIN[ch.lowercaseChar()]
        if (mapped == null) {
            append(ch)
            index++
            continue
        }
        if (mapped.isNotEmpty() && ch.isUpperCase()) {
            val next = text.getOrNull(index + 1)
            val nextUpper = next != null && next.isUpperCase() && RU_LATIN.containsKey(next.lowercaseChar())
            append(if (nextUpper) mapped.uppercase() else mapped.replaceFirstChar { it.titlecaseChar() })
        } else {
            append(mapped)
        }
        index++
    }
}

private fun copyPlain(context: Context, value: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
    manager.setPrimaryClip(ClipData.newPlainText("Транслит", value))
    true
} catch (_: RuntimeException) {
    false
}
