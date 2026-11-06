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

private const val CyrA = '\u0430'
private const val CyrE = '\u0435'
private const val CyrO = '\u043E'
private const val CyrP = '\u0440'
private const val CyrC = '\u0441'
private const val CyrU = '\u0443'
private const val CyrHa = '\u0445'
private const val CyrAUpper = '\u0410'
private const val CyrEUpper = '\u0415'
private const val CyrOUpper = '\u041E'
private const val CyrPUpper = '\u0420'
private const val CyrCUpper = '\u0421'
private const val CyrUUpper = '\u0423'
private const val CyrHaUpper = '\u0425'

/** Латиница → похожая кириллица: a/а, e/е, o/о, p/р, c/с, y/у, x/х и те же заглавные. */
private val LatinTwin = mapOf(
    'a' to CyrA,
    'e' to CyrE,
    'o' to CyrO,
    'p' to CyrP,
    'c' to CyrC,
    'y' to CyrU,
    'x' to CyrHa,
    'A' to CyrAUpper,
    'E' to CyrEUpper,
    'O' to CyrOUpper,
    'P' to CyrPUpper,
    'C' to CyrCUpper,
    'Y' to CyrUUpper,
    'X' to CyrHaUpper,
)

private val CyrillicTwin = LatinTwin.entries.associate { (latin, cyrillic) -> cyrillic to latin }

/**
 * Предупреждение, если имя файла смешивает кириллицу и латиницу среди пар
 * а/a, е/e, о/o, р/p, с/c, у/y, х/x (и заглавных). Позиции считаются с 1.
 */
@Composable
fun T47HomoglyphScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var name by rememberSaveable { mutableStateOf("") }
    val hits = lookalikeHits(name)
    val mixed = hits.any { it.cyrillic } && hits.any { !it.cyrillic }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Омоглифы") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Имя проверяется на смесь похожих букв кириллицы и латиницы: а/a, е/e, о/o, р/p, с/c, у/y, х/x. Заглавные учитываются так же.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = mixed,
                label = { Text("Имя файла") },
                placeholder = { Text("report.pdf", fontFamily = FontFamily.Monospace) },
                supportingText = {
                    Text(
                        when {
                            name.isEmpty() -> "Введите имя файла"
                            mixed -> "Смешаны похожие буквы кириллицы и латиницы"
                            else -> "Смешения нет"
                        },
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            if (name.isNotEmpty()) {
                if (mixed) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = scheme.errorContainer,
                    ) {
                        Column(
                            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                "Смешение алфавитов",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onErrorContainer,
                            )
                            Text(
                                "В одном имени есть похожие буквы обоих алфавитов. Ниже — позиции с 1 и код символа.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onErrorContainer,
                            )
                        }
                    }
                    Text(
                        "Позиции",
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onSurfaceVariant,
                    )
                    hits.forEach { hit ->
                        LookalikePosition(hit)
                    }
                } else {
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
                                "Смешения нет",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                if (hits.isEmpty()) {
                                    "Похожих букв из этих пар нет"
                                } else {
                                    "Похожие буквы только одного алфавита"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LookalikePosition(hit: LookalikeHit) {
    val scheme = MaterialTheme.colorScheme
    val script = if (hit.cyrillic) "кириллица" else "латиница"
    val other = if (hit.cyrillic) "латинскую" else "кириллическую"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = scheme.surfaceContainer,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                "Позиция ${hit.position}",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
            Text(
                "${hit.glyph}  ${code(hit.char)}  $script",
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Похожа на $other «${hit.twin}» (${code(hit.twin)})",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

private data class LookalikeHit(
    val position: Int,
    val char: Char,
    val cyrillic: Boolean,
    val twin: Char,
) {
    val glyph: String get() = char.toString()
}

/** Позиция — номер символа в строке имени, начиная с 1. Учитываются только пары а/a … х/x. */
private fun lookalikeHits(name: String): List<LookalikeHit> {
    if (name.isEmpty()) return emptyList()
    val hits = ArrayList<LookalikeHit>()
    for (index in name.indices) {
        val char = name[index]
        val cyrillicTwin = CyrillicTwin[char]
        val latinTwin = LatinTwin[char]
        when {
            cyrillicTwin != null -> hits.add(LookalikeHit(index + 1, char, cyrillic = true, twin = cyrillicTwin))
            latinTwin != null -> hits.add(LookalikeHit(index + 1, char, cyrillic = false, twin = latinTwin))
        }
    }
    return hits
}

private fun code(char: Char): String = "U+%04X".format(char.code)
