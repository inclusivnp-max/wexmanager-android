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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private const val FormatHint = "Нужен формат major.minor.patch, например 1.4.2"
private const val PartHint = "Каждая часть — неотрицательное целое без ведущих нулей"
private const val RangeHint = "Число слишком большое"

@Composable
fun T27SemverScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var firstText by rememberSaveable { mutableStateOf("") }
    var secondText by rememberSaveable { mutableStateOf("") }
    val first = parseSemver(firstText)
    val second = parseSemver(secondText)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Сравнение версий") },
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
                "Сравните две версии вида major.minor.patch. Старше та, у которой больше число слева.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            VersionField(
                value = firstText,
                onValueChange = { firstText = it },
                label = "Первая версия",
                placeholder = "1.2.3",
                parse = first,
            )
            VersionField(
                value = secondText,
                onValueChange = { secondText = it },
                label = "Вторая версия",
                placeholder = "1.4.0",
                parse = second,
            )
            ComparisonResult(first, second)
        }
    }
}

@Composable
private fun VersionField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    parse: SemParse,
) {
    val error = (parse as? SemParse.Invalid)?.message
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        supportingText = { Text(error ?: "major.minor.patch") },
        isError = error != null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Composable
private fun ComparisonResult(first: SemParse, second: SemParse) {
    val scheme = MaterialTheme.colorScheme
    val verdict = when {
        first is SemParse.Ok && second is SemParse.Ok -> compareVerdict(first.version, second.version)
        first is SemParse.Invalid || second is SemParse.Invalid -> null
        first is SemParse.Empty && second is SemParse.Empty -> null
        else -> "Введите обе версии"
    } ?: return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainer,
    ) {
        Text(
            verdict,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
            color = scheme.onSurface,
        )
    }
}

private fun compareVerdict(first: SemVer, second: SemVer): String {
    val order = first.compareTo(second)
    return when {
        order == 0 -> "Версии равны: $first"
        order > 0 -> "Новее первая: $first новее $second"
        else -> "Новее вторая: $second новее $first"
    }
}

private data class SemVer(val major: Long, val minor: Long, val patch: Long) : Comparable<SemVer> {
    override fun compareTo(other: SemVer): Int {
        val byMajor = major.compareTo(other.major)
        if (byMajor != 0) return byMajor
        val byMinor = minor.compareTo(other.minor)
        if (byMinor != 0) return byMinor
        return patch.compareTo(other.patch)
    }

    override fun toString(): String = "$major.$minor.$patch"
}

private sealed interface SemParse {
    data object Empty : SemParse
    data class Invalid(val message: String) : SemParse
    data class Ok(val version: SemVer) : SemParse
}

private fun parseSemver(raw: String): SemParse {
    val text = raw.trim()
    if (text.isEmpty()) return SemParse.Empty
    val parts = text.split('.')
    if (parts.size != 3) return SemParse.Invalid(FormatHint)
    val numbers = LongArray(3)
    for (index in parts.indices) {
        val part = parts[index]
        if (part.isEmpty() || part.any { !it.isDigit() }) return SemParse.Invalid(PartHint)
        if (part.length > 1 && part[0] == '0') return SemParse.Invalid(PartHint)
        numbers[index] = part.toLongOrNull() ?: return SemParse.Invalid(RangeHint)
    }
    return SemParse.Ok(SemVer(numbers[0], numbers[1], numbers[2]))
}
