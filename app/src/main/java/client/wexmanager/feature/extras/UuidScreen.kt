package client.wexmanager.feature.extras

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.util.UUID

private const val HistorySize = 5

@Composable
fun UuidScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val recent = remember { mutableStateListOf<String>() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("UUID") },
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
        containerColor = scheme.surface,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Button(
                onClick = {
                    recent.add(0, UUID.randomUUID().toString())
                    while (recent.size > HistorySize) recent.removeAt(recent.lastIndex)
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
            ) {
                Text("Новый UUID")
            }
            if (recent.isEmpty()) {
                Text(
                    "Здесь появятся последние 5 UUID. Нажмите строку, чтобы скопировать.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "Последние ${recent.size}",
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(recent) { uuid ->
                        ListItem(
                            headlineContent = {
                                Text(uuid, fontFamily = FontFamily.Monospace)
                            },
                            supportingContent = { Text("Нажмите, чтобы скопировать") },
                            colors = ListItemDefaults.colors(containerColor = scheme.surface),
                            modifier = Modifier.clickable {
                                val copied = runCatching {
                                    val cm = context.getSystemService(ClipboardManager::class.java)
                                        ?: error("clipboard")
                                    cm.setPrimaryClip(ClipData.newPlainText("UUID", uuid))
                                }.isSuccess
                                Toast.makeText(
                                    context,
                                    if (copied) "Скопировано" else "Не удалось скопировать",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
