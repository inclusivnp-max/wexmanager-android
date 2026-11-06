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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Восьмеричные права вроде 755 показываются как rwxr-xr-x. Файлы не читаются и не меняются. */
@Composable
fun T09ChmodScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var octal by rememberSaveable { mutableStateOf("755") }
    val parsed = parseChmodOctal(octal)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Права chmod") },
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
                "Три цифры — владелец, группа и остальные. 4 — чтение (r), 2 — запись (w), 1 — выполнение (x). 755 даёт rwxr-xr-x.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = octal,
                onValueChange = { next ->
                    octal = next.filter { it in '0'..'7' }.take(4)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Восьмеричные права") },
                placeholder = { Text("755", fontFamily = FontFamily.Monospace) },
                supportingText = { Text("От 1 до 4 цифр 0–7. Четвёртая слева — setuid, setgid и sticky.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            if (parsed != null) {
                Text(
                    parsed.symbolic,
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
                ClassCard("Владелец", parsed.owner, parsed.ownerText)
                ClassCard("Группа", parsed.group, parsed.groupText)
                ClassCard("Другие", parsed.other, parsed.otherText)
                if (parsed.specialText != null) {
                    Text(
                        parsed.specialText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClassCard(title: String, triad: String, explanation: String) {
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
                triad,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
            Text(explanation, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private data class ChmodView(
    val symbolic: String,
    val owner: String,
    val group: String,
    val other: String,
    val ownerText: String,
    val groupText: String,
    val otherText: String,
    val specialText: String?,
)

private fun parseChmodOctal(raw: String): ChmodView? {
    val text = raw.trim()
    if (text.isEmpty() || text.length > 4 || text.any { it !in '0'..'7' }) return null
    val value = text.toInt(8)
    val special = (value shr 9) and 7
    val ownerBits = (value shr 6) and 7
    val groupBits = (value shr 3) and 7
    val otherBits = value and 7
    val owner = triad(ownerBits, special and 4 != 0, 's')
    val group = triad(groupBits, special and 2 != 0, 's')
    val other = triad(otherBits, special and 1 != 0, 't')
    return ChmodView(
        symbolic = owner + group + other,
        owner = owner,
        group = group,
        other = other,
        ownerText = rightsSentence(plural = false, owner),
        groupText = rightsSentence(plural = false, group),
        otherText = rightsSentence(plural = true, other),
        specialText = specialNote(special),
    )
}

private fun triad(bits: Int, special: Boolean, lower: Char): String {
    val read = if (bits and 4 != 0) 'r' else '-'
    val write = if (bits and 2 != 0) 'w' else '-'
    val execute = when {
        special && bits and 1 != 0 -> lower
        special -> lower.uppercaseChar()
        bits and 1 != 0 -> 'x'
        else -> '-'
    }
    return "$read$write$execute"
}

private fun rightsSentence(plural: Boolean, triad: String): String {
    val read = triad[0] == 'r'
    val write = triad[1] == 'w'
    val execute = triad[2] == 'x' || triad[2] == 's' || triad[2] == 't'
    val can = if (plural) "Могут" else "Может"
    val cannot = if (plural) "не могут" else "не может"
    val actions = buildList {
        if (read) add("читать")
        if (write) add("изменять")
        if (execute) add("запускать")
    }
    return when (actions.size) {
        0 -> "${if (plural) "Не могут" else "Не может"} ни читать, ни изменять, ни запускать."
        1 -> "$can только ${actions[0]}."
        2 -> "$can ${actions[0]} и ${actions[1]}, но $cannot ${missing(read, write, execute)}."
        else -> "$can читать, изменять и запускать."
    }
}

private fun missing(read: Boolean, write: Boolean, execute: Boolean): String = when {
    !read -> "читать"
    !write -> "изменять"
    else -> "запускать"
}

private fun specialNote(special: Int): String? {
    if (special == 0) return null
    val parts = buildList {
        if (special and 4 != 0) add("setuid — запуск от имени владельца")
        if (special and 2 != 0) add("setgid — запуск от имени группы")
        if (special and 1 != 0) add("sticky — в каталоге чужой файл не удалить")
    }
    return parts.joinToString(". ", postfix = ".")
        .replaceFirstChar { it.uppercaseChar() }
}
