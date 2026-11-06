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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Две фразы — анаграммы, если совпадает набор символов без пробелов и без учёта регистра. */
@Composable
fun T96AnagramScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var first by rememberSaveable { mutableStateOf("") }
    var second by rememberSaveable { mutableStateOf("") }
    val ready = first.any { !it.isWhitespace() } && second.any { !it.isWhitespace() }
    val same = ready && anagramKey(first) == anagramKey(second)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Анаграммы") },
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
                "Сравнивает две фразы. Пробелы и переводы строк отбрасываются, регистр не учитывается. " +
                    "Знаки препинания остаются и входят в набор символов.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = first,
                onValueChange = { first = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Первая фраза") },
                placeholder = { Text("кот летает") },
                minLines = 2,
                maxLines = 6,
            )
            OutlinedTextField(
                value = second,
                onValueChange = { second = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Вторая фраза") },
                placeholder = { Text("толкает те") },
                minLines = 2,
                maxLines = 6,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = when {
                    !ready -> scheme.surfaceContainer
                    same -> scheme.primaryContainer
                    else -> scheme.errorContainer
                },
            ) {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        when {
                            !ready -> "Введите обе фразы"
                            same -> "Это анаграммы"
                            else -> "Это не анаграммы"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            !ready -> scheme.onSurface
                            same -> scheme.onPrimaryContainer
                            else -> scheme.onErrorContainer
                        },
                    )
                    Text(
                        when {
                            !ready -> "Нужен хотя бы один непробельный символ в каждой"
                            same -> "Набор символов совпадает"
                            else -> "Набор символов различается"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            !ready -> scheme.onSurfaceVariant
                            same -> scheme.onPrimaryContainer
                            else -> scheme.onErrorContainer
                        },
                    )
                }
            }
        }
    }
}

private fun anagramKey(phrase: String): String =
    phrase.filterNot { it.isWhitespace() }
        .lowercase(Locale.ROOT)
        .toCharArray()
        .apply { sort() }
        .concatToString()
