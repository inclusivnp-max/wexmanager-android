package client.wexmanager.feature.browser

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import client.wexmanager.core.fs.FileItem
import client.wexmanager.core.settings.AppSettings
import client.wexmanager.core.settings.ListDensity
import client.wexmanager.core.util.Format
import client.wexmanager.ui.components.FileIcon
import client.wexmanager.ui.theme.LocalAppSettings

fun ListDensity.iconSize(): Dp = when (this) {
    ListDensity.Compact -> 34.dp
    ListDensity.Comfortable -> 40.dp
    ListDensity.Spacious -> 48.dp
}

fun ListDensity.verticalPadding(): Dp = when (this) {
    ListDensity.Compact -> 4.dp
    ListDensity.Comfortable -> 7.dp
    ListDensity.Spacious -> 12.dp
}

fun FileItem.subtitle(s: AppSettings): String {
    val date = Format.date(lastModified, s.dateFormat)
    val first = when {
        isDirectory && size > 0 && childCount >= 0 -> "${Format.items(childCount)}  •  ${Format.size(size, s.sizeUnits)}"
        isDirectory && size > 0 -> Format.size(size, s.sizeUnits)
        isDirectory && childCount >= 0 -> Format.items(childCount)
        isDirectory -> "Папка"
        else -> Format.size(size, s.sizeUnits)
    }
    return if (lastModified > 0) "$first  •  $date" else first
}

@Composable
private fun rememberHighlight(highlight: Boolean): Float {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(highlight) {
        if (highlight) {
            anim.animateTo(1f, androidx.compose.animation.core.tween(220))
            kotlinx.coroutines.delay(650)
            anim.animateTo(0f, androidx.compose.animation.core.tween(900))
        }
    }
    return anim.value
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    item: FileItem,
    selected: Boolean,
    highlight: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onIconClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalAppSettings.current
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        if (selected) scheme.secondaryContainer else Color.Transparent,
        MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "rowBg",
    )
    val glow = rememberHighlight(highlight)
    val shape = RoundedCornerShape(if (selected) 22.dp else 18.dp)
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(shape)
            .drawBehind {
                drawRect(bg)
                if (glow > 0f) drawRect(scheme.tertiary.copy(alpha = glow * 0.22f))
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = ripple(),
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 10.dp, vertical = s.listDensity.verticalPadding()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconSize = s.listDensity.iconSize()
        FileIcon(
            item = item,
            selected = selected,
            size = iconSize,
            modifier = if (s.tapIconToSelect) Modifier
                .clip(RoundedCornerShape(30))
                .combinedClickable(onClick = onIconClick, onLongClick = onLongClick) else Modifier,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            FileName(item, s, selected)
            val sub = remember(item, s.dateFormat, s.sizeUnits) { item.subtitle(s) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) scheme.onSecondaryContainer.copy(alpha = 0.8f) else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (s.showPermissions && item.mode >= 0) {
                    Text(
                        "  ${item.permissionString}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        color = scheme.outline,
                        maxLines = 1,
                    )
                }
            }
            item.linkTarget?.let { target ->
                Text(
                    "→ $target",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.tertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FileName(item: FileItem, s: AppSettings, selected: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val base = MaterialTheme.typography.bodyLarge
    val style = base.copy(fontSize = base.fontSize * s.fileListFontScale, lineHeight = base.lineHeight * s.fileListFontScale)
    val color = when {
        selected -> scheme.onSecondaryContainer
        item.isHidden -> scheme.onSurface.copy(alpha = 0.6f)
        else -> scheme.onSurface
    }
    // Строка с приглушённым расширением собирается один раз, а не на каждый кадр прокрутки.
    val text = remember(item.name, item.isDirectory, s.showExtensions, color) {
        val ext = item.extension
        if (item.isDirectory || ext.isEmpty() || item.name.startsWith('.') && item.name.count { it == '.' } == 1) {
            AnnotatedString(item.name)
        } else if (!s.showExtensions) {
            AnnotatedString(item.baseName)
        } else {
            buildAnnotatedString {
                append(item.baseName)
                withStyle(SpanStyle(color = color.copy(alpha = 0.55f))) { append("." + item.name.substringAfterLast('.')) }
            }
        }
    }
    Text(
        text,
        style = style,
        color = color,
        maxLines = s.fileNameMaxLines,
        overflow = if (s.fileNameMaxLines == 1) TextOverflow.MiddleEllipsis else TextOverflow.Ellipsis,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridCell(
    item: FileItem,
    selected: Boolean,
    highlight: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onIconClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalAppSettings.current
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        if (selected) scheme.secondaryContainer else scheme.surfaceContainerLow,
        MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "cellBg",
    )
    val glow = rememberHighlight(highlight)
    Column(
        modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(22.dp))
            .drawBehind {
                drawRect(bg)
                if (glow > 0f) drawRect(scheme.tertiary.copy(alpha = glow * 0.22f))
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val fullBleed = s.showThumbnails && !item.isInArchive &&
            (item.kind == client.wexmanager.core.fs.FileKind.Image || item.kind == client.wexmanager.core.fs.FileKind.Video ||
                item.kind == client.wexmanager.core.fs.FileKind.Pdf)
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            if (fullBleed) {
                // Превью на всю ячейку; поверх — значок выделения и метка видео.
                val model = remember(item.path, item.lastModified) {
                    if (item.kind == client.wexmanager.core.fs.FileKind.Pdf) client.wexmanager.core.icons.PdfThumbModel(item.path, item.lastModified)
                    else java.io.File(item.path)
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(scheme.surfaceContainerHighest)
                        .combinedClickable(onClick = onClick, onLongClick = onLongClick),
                ) {
                    // Миниатюра не получилась (зашифрованный PDF, битое видео) — вместо пустой плитки обычная иконка.
                    var failed by remember(model) { mutableStateOf(false) }
                    if (failed) {
                        FileIcon(item = item, selected = false, size = 64.dp, showThumbnail = false, modifier = Modifier.align(Alignment.Center))
                    } else {
                        coil3.compose.AsyncImage(
                            model = model,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            alignment = if (item.kind == client.wexmanager.core.fs.FileKind.Pdf) Alignment.TopCenter else Alignment.Center,
                            onError = { failed = true },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    val circle by animateColorAsState(
                        if (selected) scheme.primary else Color.Black.copy(alpha = 0.28f),
                        MaterialTheme.motionScheme.fastEffectsSpec(),
                        label = "selCircle",
                    )
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .size(26.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(circle)
                            .border(1.5.dp, Color.White.copy(alpha = if (selected) 0f else 0.9f), androidx.compose.foundation.shape.CircleShape)
                            .combinedClickable(onClick = onIconClick, onLongClick = onLongClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) androidx.compose.material3.Icon(
                            Icons.Rounded.Check, "Выбрано",
                            tint = scheme.onPrimary, modifier = Modifier.size(16.dp),
                        )
                    }
                    if (item.kind == client.wexmanager.core.fs.FileKind.Video) {
                        androidx.compose.material3.Icon(
                            Icons.Rounded.PlayCircle, null,
                            tint = Color.White, modifier = Modifier.align(Alignment.Center).size(36.dp),
                        )
                    }
                }
            } else {
                FileIcon(
                    item = item,
                    selected = selected,
                    size = 64.dp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(30))
                        .combinedClickable(onClick = onIconClick, onLongClick = onLongClick),
                )
            }
        }
        Text(
            item.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
            // Две строки всегда: ячейки одного ряда одинаковой высоты, сетка не «пляшет».
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.MiddleEllipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            if (item.isDirectory) (if (item.childCount >= 0) Format.items(item.childCount) else "Папка") else Format.size(item.size, s.sizeUnits),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
fun Dot() = Box(Modifier.size(3.dp).background(MaterialTheme.colorScheme.outline, RoundedCornerShape(50)))
