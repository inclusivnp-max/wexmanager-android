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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp

@Composable
fun T12PathJoinScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var root by rememberSaveable { mutableStateOf("") }
    var relative by rememberSaveable { mutableStateOf("") }
    var joined by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Склейка путей") },
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
                "Корень и относительный путь склеиваются в один. «.» убирается, «..» поднимает на уровень вверх и отклоняется, только если выходит за корень.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = root,
                onValueChange = {
                    root = it
                    joined = null
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Корень") },
                placeholder = { Text("/storage/emulated/0") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            OutlinedTextField(
                value = relative,
                onValueChange = {
                    relative = it
                    joined = null
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Относительный путь") },
                placeholder = { Text("Download/file.txt") },
                isError = error != null,
                supportingText = error?.let { message -> { Text(message) } },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            Button(
                onClick = {
                    when (val outcome = joinUnderRoot(root, relative)) {
                        is PathJoin.Ok -> {
                            joined = outcome.path
                            error = null
                        }
                        is PathJoin.Fail -> {
                            joined = null
                            error = outcome.message
                        }
                    }
                },
                shapes = ButtonDefaults.shapes(),
            ) { Text("Склеить") }
            joined?.let { path ->
                Text(
                    "Результат",
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    path,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    color = scheme.onSurface,
                )
            }
        }
    }
}

private sealed interface PathJoin {
    data class Ok(val path: String) : PathJoin
    data class Fail(val message: String) : PathJoin
}

/**
 * Склеивает [root] и [relative]. «..» может сокращать сегменты только внутри корня:
 * подъём выше нормализованного корня — ошибка.
 */
private fun joinUnderRoot(root: String, relative: String): PathJoin {
    val rootText = root.trim().replace('\\', '/')
    if (rootText.isEmpty()) return PathJoin.Fail("Укажите корневой путь")
    val absolute = rootText.startsWith("/")
    val rootSegments = normalizeRoot(rootText, absolute)
    val floor = rootSegments.size
    val stack = rootSegments.toMutableList()
    val relativeText = relative.trim().replace('\\', '/')
    for (segment in relativeText.split('/')) {
        val part = segment.trim()
        when {
            part.isEmpty() || part == "." -> Unit
            part == ".." -> {
                if (stack.size <= floor) {
                    return PathJoin.Fail("Путь «..» выходит за пределы корня")
                }
                stack.removeAt(stack.lastIndex)
            }
            else -> stack.add(part)
        }
    }
    return PathJoin.Ok(formatPath(absolute, stack))
}

/** «..» в самом корне разрешается; у абсолютного пути выше «/» подняться нельзя. */
private fun normalizeRoot(rootText: String, absolute: Boolean): MutableList<String> {
    val segments = mutableListOf<String>()
    for (segment in rootText.split('/')) {
        val part = segment.trim()
        when {
            part.isEmpty() || part == "." -> Unit
            part == ".." -> {
                if (segments.isNotEmpty() && segments.last() != "..") {
                    segments.removeAt(segments.lastIndex)
                } else if (!absolute) {
                    segments.add("..")
                }
            }
            else -> segments.add(part)
        }
    }
    return segments
}

private fun formatPath(absolute: Boolean, segments: List<String>): String {
    if (segments.isEmpty()) return if (absolute) "/" else "."
    val body = segments.joinToString("/")
    return if (absolute) "/$body" else body
}
