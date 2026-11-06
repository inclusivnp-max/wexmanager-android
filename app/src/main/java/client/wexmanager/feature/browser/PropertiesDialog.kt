package client.wexmanager.feature.browser

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import client.wexmanager.core.fs.FileItem
import client.wexmanager.core.fs.LocalFileSystem
import client.wexmanager.core.fs.Permissions
import client.wexmanager.core.util.FileOpener
import client.wexmanager.core.util.Format
import client.wexmanager.core.util.HashAlgorithm
import client.wexmanager.core.util.Hashing
import client.wexmanager.ui.components.FileIcon
import client.wexmanager.ui.components.SectionLabel
import client.wexmanager.ui.theme.LocalAppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PropertiesDialog(items: List<FileItem>, vm: BrowserViewModel, onDismiss: () -> Unit) {
    val s = LocalAppSettings.current
    val context = LocalContext.current
    val single = items.singleOrNull()
    var files by remember { mutableIntStateOf(0) }
    var dirs by remember { mutableIntStateOf(0) }
    var bytes by remember { mutableStateOf(0L) }
    var counting by remember { mutableStateOf(true) }
    var chmodOpen by remember { mutableStateOf(false) }
    var imageSize by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(items) {
        var f = 0
        var d = 0
        var b = 0L
        for (item in items) {
            if (item.isInArchive) {
                if (item.isDirectory) d++ else { f++; b += maxOf(item.size, 0) }
                continue
            }
            if (item.isDirectory) {
                d++
                val baseF = f; val baseD = d; val baseB = b
                val (cf, cd, cb) = LocalFileSystem.directorySize(item.path) { pf, pd, pb ->
                    files = baseF + pf; dirs = baseD + pd; bytes = baseB + pb
                }
                f += cf; d += cd; b += cb
            } else {
                f++
                b += item.size
            }
            files = f; dirs = d; bytes = b
        }
        counting = false
        if (single != null && !single.isInArchive && single.kind == client.wexmanager.core.fs.FileKind.Image) {
            imageSize = withContext(Dispatchers.IO) {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(single.path, o)
                if (o.outWidth > 0) "${o.outWidth} × ${o.outHeight} px" else null
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (single != null) {
                    FileIcon(single, size = 44.dp)
                    Spacer(Modifier.width(14.dp))
                }
                Text(
                    single?.name ?: "Выбрано: ${items.size}",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (single != null) {
                    PropRow("Путь", single.key, copy = true)
                    PropRow(
                        "Тип",
                        if (single.isDirectory) "Папка" else "${single.kind.label} • ${FileOpener.mimeType(single.name)}",
                        copy = true,
                    )
                    if (single.isSymlink) PropRow("Ссылка на", single.linkTarget ?: "?", copy = true)
                }
                PropRow(
                    "Размер",
                    buildString {
                        append(Format.size(bytes, s.sizeUnits))
                        if (bytes >= 1024) append(" (${Format.bytesExact(bytes)})")
                        if (counting) append(" …")
                    },
                    copy = true,
                )
                if (items.any { it.isDirectory } || items.size > 1) {
                    PropRow(
                        "Содержит",
                        "${Format.files(files)}, ${Format.folders(maxOf(dirs - items.count { it.isDirectory }, 0))}",
                        copy = true,
                    )
                }
                if (counting) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 6.dp))
                imageSize?.let { PropRow("Разрешение", it, copy = true) }
                if (single != null) {
                    PropRow("Изменён", Format.fullDate(single.lastModified), copy = true)
                    if (single.mode >= 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                PropRow(
                                    "Права",
                                    "${single.permissionString}  (${Permissions.toOctal(single.mode)})",
                                    copy = true,
                                    mono = true,
                                )
                            }
                            if (!single.isInArchive) IconButton(onClick = { chmodOpen = true }) { Icon(Icons.Rounded.Lock, "Изменить права") }
                        }
                        if (single.uid >= 0) {
                            PropRow("Владелец", "uid ${single.uid} / gid ${single.gid}", copy = true, mono = true)
                        }
                    }
                    if (!single.isDirectory && !single.isInArchive) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        ChecksumSection(File(single.path))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
    if (chmodOpen && single != null) {
        ChmodDialog(single, onDismiss = { chmodOpen = false }) { mode ->
            vm.chmod(single, mode) { err ->
                if (err != null) android.widget.Toast.makeText(context, "Ошибка: $err", android.widget.Toast.LENGTH_SHORT).show()
            }
            chmodOpen = false
            onDismiss()
        }
    }
}

@Composable
fun PropRow(label: String, value: String, copy: Boolean = false, mono: Boolean = false) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(if (copy) Modifier.clickable { copyToSystemClipboard(context, value) } else Modifier)
            .padding(vertical = 5.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            value,
            style = if (mono) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ChecksumSection(file: File) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var progress by remember { mutableFloatStateOf(0f) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Map<HashAlgorithm, String>>(emptyMap()) }
    var job by remember { mutableStateOf<Job?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Fingerprint, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text("Контрольные суммы", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (result.isEmpty() && !running) {
            FilledTonalButton(onClick = {
                running = true
                job = scope.launch {
                    result = runCatching {
                        Hashing.compute(file, listOf(HashAlgorithm.MD5, HashAlgorithm.SHA1, HashAlgorithm.SHA256, HashAlgorithm.CRC32)) { progress = it }
                    }.getOrDefault(emptyMap())
                    running = false
                }
            }) { Text("Вычислить") }
        }
    }
    AnimatedVisibility(running) {
        LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
    }
    result.forEach { (alg, hash) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(alg.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(hash, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
            }
            IconButton(onClick = { copyToSystemClipboard(context, hash) }) { Icon(Icons.Rounded.ContentCopy, "Копировать") }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun ChmodDialog(item: FileItem, onDismiss: () -> Unit, onApply: (Int) -> Unit) {
    var mode by remember { mutableIntStateOf(item.mode and 0xFFF) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Lock, null) },
        title = { Text("Права доступа") },
        text = {
            Column {
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(96.dp))
                    listOf("Чтение", "Запись", "Выполн.").forEach {
                        Text(it, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    }
                }
                listOf("Владелец" to 6, "Группа" to 3, "Другие" to 0).forEach { (label, shift) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, modifier = Modifier.width(96.dp))
                        for (bit in 2 downTo 0) {
                            val mask = 1 shl (shift + bit)
                            Row(Modifier.weight(1f)) {
                                Checkbox(checked = mode and mask != 0, onCheckedChange = { mode = if (it) mode or mask else mode and mask.inv() })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "${Permissions.toString(mode, item.isDirectory, false)}   ${Permissions.toOctal(mode)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                )
            }
        },
        confirmButton = { Button(onClick = { onApply(mode) }) { Text("Применить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
