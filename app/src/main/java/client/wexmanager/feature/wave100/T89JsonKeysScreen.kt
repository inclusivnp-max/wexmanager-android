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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Ключи верхнего уровня объекта JSON. Вложенные объекты не раскрываются. */
@Composable
fun T89JsonKeysScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var source by rememberSaveable { mutableStateOf("") }
    var packed by rememberSaveable { mutableStateOf("") }
    var ready by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val keys = remember(packed, ready) { unpackKeys(packed, ready) }

    fun copyKeys() {
        val value = keys.joinToString("\n")
        val copied = value.isNotEmpty() && copyPlain(context, value)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                when {
                    value.isEmpty() -> "Нечего копировать"
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
                title = { Text("Ключи JSON") },
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Объект JSON: в списке только ключи верхнего уровня. Вложенные объекты и массивы не раскрываются.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = source,
                onValueChange = {
                    source = it
                    error = null
                    ready = false
                    packed = ""
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("JSON") },
                placeholder = { Text("{\n  \"a\": 1,\n  \"b\": { \"c\": 2 }\n}") },
                isError = error != null,
                supportingText = error?.let { message -> { Text(message) } },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                minLines = 6,
                maxLines = 10,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        if (source.isBlank()) {
                            packed = ""
                            ready = false
                            error = null
                            return@Button
                        }
                        try {
                            packed = packKeys(topLevelKeys(source))
                            ready = true
                            error = null
                        } catch (e: JSONException) {
                            packed = ""
                            ready = false
                            val line = e.message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
                            error = "Не JSON: $line"
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Показать") }
                FilledTonalButton(
                    onClick = ::copyKeys,
                    enabled = keys.isNotEmpty(),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Копировать")
                }
            }
            when {
                error != null -> Unit
                !ready -> Text(
                    "Вставьте объект — здесь будут ключи верхнего уровня",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                keys.isEmpty() -> Text(
                    "В объекте нет ключей",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                else -> {
                    Text(
                        keysLabel(keys.size),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(keys, key = { it }) { key ->
                            Text(
                                key.ifEmpty { "\"\"" },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = FontFamily.Monospace,
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

private fun topLevelKeys(raw: String): List<String> {
    val obj = JSONObject(raw.trim())
    val keys = ArrayList<String>(obj.length())
    val iterator = obj.keys()
    while (iterator.hasNext()) keys.add(iterator.next())
    return keys
}

private fun packKeys(keys: List<String>): String = JSONArray().apply {
    keys.forEach { put(it) }
}.toString()

private fun unpackKeys(packed: String, ready: Boolean): List<String> {
    if (!ready || packed.isEmpty()) return emptyList()
    val array = JSONArray(packed)
    return List(array.length()) { array.getString(it) }
}

private fun keysLabel(n: Int): String {
    val word = when {
        n % 100 in 11..14 -> "ключей"
        n % 10 == 1 -> "ключ"
        n % 10 in 2..4 -> "ключа"
        else -> "ключей"
    }
    return "$n $word"
}

private fun copyPlain(context: Context, value: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
    manager.setPrimaryClip(ClipData.newPlainText("Ключи JSON", value))
    true
} catch (_: RuntimeException) {
    false
}
