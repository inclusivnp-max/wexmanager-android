package client.wexmanager.feature.extras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

private const val SAMPLE_LIMIT = 100_000
private const val FIND_LIMIT = 20

/** Проверка шаблона java.util.regex.Pattern. Образец обрезается до 100 000 символов. */
@Composable
fun RegexScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var patternText by rememberSaveable { mutableStateOf("") }
    var sample by rememberSaveable { mutableStateOf("") }
    val evaluation = remember(patternText, sample) { evaluate(patternText, sample) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Регулярные выражения") },
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
            OutlinedTextField(
                value = patternText,
                onValueChange = { patternText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Шаблон") },
                isError = evaluation is RegexEvaluation.Invalid,
                supportingText = (evaluation as? RegexEvaluation.Invalid)?.let { error ->
                    { Text(error.message) }
                },
                minLines = 1,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            OutlinedTextField(
                value = sample,
                onValueChange = { sample = it.take(SAMPLE_LIMIT) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Образец") },
                supportingText = { Text("${sample.length} из 100 000") },
                minLines = 5,
                maxLines = 10,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )
            when (evaluation) {
                RegexEvaluation.Empty -> Text(
                    "Введите шаблон",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                is RegexEvaluation.Invalid -> Unit
                is RegexEvaluation.Ok -> RegexResult(evaluation)
            }
        }
    }
}

@Composable
private fun RegexResult(result: RegexEvaluation.Ok) {
    val scheme = MaterialTheme.colorScheme
    val mono = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainer,
    ) {
        SelectionContainer {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (result.fullMatch) "Совпадает" else "Не совпадает",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (result.fullMatch) scheme.primary else scheme.onSurface,
                )
                if (result.finds.isEmpty()) {
                    Text(
                        "Вхождений нет",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                } else {
                    result.finds.forEachIndexed { index, groups ->
                        Text(
                            "Вхождение ${index + 1}",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = if (index == 0) 4.dp else 8.dp),
                        )
                        groups.forEachIndexed { groupIndex, value ->
                            Text("$groupIndex: ${value ?: "null"}", style = mono)
                        }
                    }
                    if (result.more) {
                        Text(
                            "Показаны первые 20 вхождений",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private sealed interface RegexEvaluation {
    data object Empty : RegexEvaluation
    data class Invalid(val message: String) : RegexEvaluation
    data class Ok(val fullMatch: Boolean, val finds: List<List<String?>>, val more: Boolean) : RegexEvaluation
}

private fun evaluate(patternText: String, sample: String): RegexEvaluation {
    if (patternText.isEmpty()) return RegexEvaluation.Empty
    val text = sample.take(SAMPLE_LIMIT)
    val compiled = try {
        Pattern.compile(patternText)
    } catch (error: PatternSyntaxException) {
        return RegexEvaluation.Invalid(error.message ?: "Ошибка в шаблоне")
    }
    val fullMatch = compiled.matcher(text).matches()
    val matcher = compiled.matcher(text)
    val finds = ArrayList<List<String?>>(FIND_LIMIT)
    var more = false
    while (matcher.find()) {
        if (finds.size == FIND_LIMIT) {
            more = true
            break
        }
        val groups = ArrayList<String?>(matcher.groupCount() + 1)
        for (index in 0..matcher.groupCount()) groups.add(matcher.group(index))
        finds.add(groups)
    }
    return RegexEvaluation.Ok(fullMatch, finds, more)
}
