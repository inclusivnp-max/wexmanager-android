package client.wexmanager.feature.wave100

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Девять символов вроде rwxr-xr-x становятся восьмеричными правами. Файлы не читаются и не меняются. */
@Composable
fun T85RwxScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var symbolic by rememberSaveable { mutableStateOf("rwxr-xr-x") }
    val parsed = parseRwx(symbolic)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Права rwx") },
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
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Девять знаков: r, w, x или дефис. Три группы — владелец, группа и остальные. rwxr-xr-x даёт 755.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = symbolic,
                onValueChange = { symbolic = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = parsed is RwxParse.Invalid,
                label = { Text("Символьные права") },
                placeholder = { Text("rwxr-xr-x", fontFamily = FontFamily.Monospace) },
                supportingText = {
                    Text(
                        when (parsed) {
                            is RwxParse.Invalid -> parsed.message
                            else -> "Только буквы и дефисы, ровно 9 символов."
                        },
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            if (parsed is RwxParse.Ok) {
                Text(
                    parsed.octal,
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
                RwxClassCard("Владелец", parsed.owner, parsed.ownerTriad)
                RwxClassCard("Группа", parsed.group, parsed.groupTriad)
                RwxClassCard("Другие", parsed.other, parsed.otherTriad)
            }
        }
    }
}

@Composable
private fun RwxClassCard(title: String, digit: Int, triad: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                digit.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
            Text(triad, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
        }
    }
}

private sealed interface RwxParse {
    data class Ok(
        val octal: String,
        val owner: Int,
        val group: Int,
        val other: Int,
        val ownerTriad: String,
        val groupTriad: String,
        val otherTriad: String,
    ) : RwxParse

    data class Invalid(val message: String) : RwxParse
}

private val SlotLetters = charArrayOf('r', 'w', 'x', 'r', 'w', 'x', 'r', 'w', 'x')

private fun parseRwx(raw: String): RwxParse? {
    if (raw.isEmpty()) return null
    if (raw.any { it != '-' && !it.isLetter() }) {
        return RwxParse.Invalid("Допустимы только буквы и дефисы.")
    }
    if (raw.length != 9) {
        return RwxParse.Invalid("Нужно ровно 9 символов, например rwxr-xr-x.")
    }
    val text = raw.lowercase()
    val digits = IntArray(3)
    for (index in text.indices) {
        val symbol = text[index]
        if (symbol == '-') continue
        val expected = SlotLetters[index]
        if (symbol != expected) {
            val place = index + 1
            return RwxParse.Invalid("На месте $place допустимы «$expected» или «-».")
        }
        val shift = 2 - (index % 3)
        digits[index / 3] = digits[index / 3] or (1 shl shift)
    }
    return RwxParse.Ok(
        octal = "${digits[0]}${digits[1]}${digits[2]}",
        owner = digits[0],
        group = digits[1],
        other = digits[2],
        ownerTriad = text.substring(0, 3),
        groupTriad = text.substring(3, 6),
        otherTriad = text.substring(6, 9),
    )
}
