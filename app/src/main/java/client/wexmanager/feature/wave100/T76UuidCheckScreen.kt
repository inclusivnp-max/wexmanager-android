package client.wexmanager.feature.wave100

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private const val FormatHint = "Вид xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx, шестнадцатеричные символы"
private const val InvalidUuid = "Не UUID. Нужны 8-4-4-4-12 шестнадцатеричных символов через дефис"

private val UuidPattern = Regex(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
)

/** Проверка канонического UUID и показ полубайта версии, если он в диапазоне 1–5. Сеть не используется. */
@Composable
fun T76UuidCheckScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var text by rememberSaveable { mutableStateOf("") }
    val checked = checkUuid(text)
    val invalid = text.isNotBlank() && checked == null

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Проверка UUID") },
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
                "Строка считается UUID, если это 8-4-4-4-12 hex. Полубайт версии показывается только для версий 1–5.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = invalid,
                label = { Text("Строка") },
                placeholder = { Text("550e8400-e29b-41d4-a716-446655440000", fontFamily = FontFamily.Monospace) },
                supportingText = { Text(if (invalid) InvalidUuid else FormatHint) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            if (checked != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = scheme.surfaceContainer,
                ) {
                    Column(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            "Это UUID",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            versionLine(checked.version),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

/** Версия только если полубайт третьей группы в диапазоне 1–5. Иначе null, строка всё равно UUID. */
private data class UuidCheck(val version: Int?)

private fun checkUuid(raw: String): UuidCheck? {
    val text = raw.trim()
    if (text.isEmpty() || !UuidPattern.matches(text)) return null
    val version = text[14].digitToInt(16)
    return UuidCheck(version.takeIf { it in 1..5 })
}

private fun versionLine(version: Int?): String = when (version) {
    null -> "Версия вне 1–5, полубайт не показывается"
    1 -> "Полубайт версии: 1 (время)"
    2 -> "Полубайт версии: 2 (DCE)"
    3 -> "Полубайт версии: 3 (имя, MD5)"
    4 -> "Полубайт версии: 4 (случайный)"
    5 -> "Полубайт версии: 5 (имя, SHA-1)"
    else -> "Версия вне 1–5, полубайт не показывается"
}
