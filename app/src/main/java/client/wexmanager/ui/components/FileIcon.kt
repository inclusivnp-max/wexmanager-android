package client.wexmanager.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath as toAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.geometry.center
import client.wexmanager.core.fs.FileItem
import client.wexmanager.core.fs.FileKind
import client.wexmanager.core.icons.ApkIconModel
import client.wexmanager.core.icons.AudioCoverModel
import client.wexmanager.core.icons.IconPack
import client.wexmanager.core.settings.IconOverride
import client.wexmanager.core.settings.IconShapeOption
import client.wexmanager.core.settings.IconStyle
import client.wexmanager.ui.theme.LocalAppSettings
import client.wexmanager.ui.theme.LocalIsDarkTheme
import coil3.compose.AsyncImage
import com.materialkolor.ktx.harmonize
import com.materialkolor.ktx.toColor
import com.materialkolor.ktx.toHct
import java.io.File

@Immutable
data class IconCustomization(
    val overrides: Map<String, IconOverride> = emptyMap(),
    val pack: IconPack? = null,
    val folderColors: Map<String, Int> = emptyMap(),
)

val LocalIconCustomization = staticCompositionLocalOf { IconCustomization() }

@Immutable
data class IconColors(val container: Color, val content: Color, val border: Color?)

private val colorCache = object : LinkedHashMap<Long, IconColors>(256, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, IconColors>?) = size > 512
}

/** Тональная пара цветов для иконки (HCT), кешируется. */
fun iconColors(base: Color, style: IconStyle, dark: Boolean, surface: Color, mono: Color): IconColors {
    // В моно-стиле цвета берутся из темы — они тоже часть ключа.
    val key = (base.value.toLong() shl 8) xor (style.ordinal.toLong() shl 1) xor (if (dark) 1 else 0) xor
        (if (style == IconStyle.Mono) surface.value.toLong() * 31 + mono.value.toLong() else 0L)
    synchronized(colorCache) { colorCache[key] }?.let { return it }
    val hct = base.toHct()
    val chroma = hct.chroma
    val result = when (style) {
        IconStyle.Tonal -> IconColors(
            container = hct.withTone(if (dark) 28.0 else 92.0).withChroma(minOf(chroma, if (dark) 40.0 else 34.0)).toColor(),
            content = hct.withTone(if (dark) 84.0 else 38.0).withChroma(maxOf(chroma, 48.0)).toColor(),
            border = null,
        )
        IconStyle.Filled -> IconColors(
            container = hct.withTone(if (dark) 62.0 else 52.0).withChroma(maxOf(chroma, 52.0)).toColor(),
            content = if (dark) hct.withTone(12.0).toColor() else Color.White,
            border = null,
        )
        IconStyle.Outlined -> IconColors(
            container = Color.Transparent,
            content = hct.withTone(if (dark) 80.0 else 42.0).toColor(),
            border = hct.withTone(if (dark) 60.0 else 70.0).withChroma(minOf(chroma, 40.0)).toColor(),
        )
        IconStyle.Mono -> IconColors(container = surface, content = mono, border = null)
    }
    synchronized(colorCache) { colorCache[key] = result }
    return result
}

fun IconShapeOption.polygon(): RoundedPolygon = when (this) {
    IconShapeOption.Squircle -> MaterialShapes.Square
    IconShapeOption.Circle -> MaterialShapes.Circle
    IconShapeOption.Cookie -> MaterialShapes.Cookie9Sided
    IconShapeOption.Clover -> MaterialShapes.Clover4Leaf
    IconShapeOption.Rounded -> MaterialShapes.Square
    IconShapeOption.Pill -> MaterialShapes.Pill
}

private val selectedPolygon: RoundedPolygon get() = MaterialShapes.Cookie12Sided

private object MorphCache {
    private val morphs = HashMap<Pair<RoundedPolygon, RoundedPolygon>, Morph>()
    private val shapes = HashMap<RoundedPolygon, Shape>()

    fun morph(a: RoundedPolygon, b: RoundedPolygon): Morph = synchronized(this) { morphs.getOrPut(a to b) { Morph(a, b) } }
    fun shape(p: RoundedPolygon): Shape = synchronized(this) { shapes.getOrPut(p) { PolygonShape(p) } }
}

/** Статичная форма из полигона Material Shapes (новый Path на каждый outline — потокобезопасно). */
class PolygonShape(private val polygon: RoundedPolygon) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = polygon.toAndroidPath().asComposePath()
        path.transform(Matrix().apply { scale(size.width, size.height) })
        path.translate(size.center - path.getBounds().center)
        return Outline.Generic(path)
    }
}

/** Форма, плавно перетекающая между двумя полигонами. */
class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress, androidx.compose.ui.graphics.Path())
        path.transform(Matrix().apply { scale(size.width, size.height) })
        path.translate(size.center - path.getBounds().center)
        return Outline.Generic(path)
    }

    override fun equals(other: Any?) = other is MorphShape && other.morph === morph && other.progress == progress
    override fun hashCode() = morph.hashCode() * 31 + progress.hashCode()
}

@Composable
fun iconShapeFor(option: IconShapeOption): Shape = when (option) {
    IconShapeOption.Rounded -> RoundedCornerShape(12.dp)
    IconShapeOption.Squircle -> RoundedCornerShape(30)
    else -> remember(option) { MorphCache.shape(option.polygon()) }
}

/** Глиф для известных системных папок. */
private fun specialFolderGlyph(name: String): ImageVector? = when (name.lowercase()) {
    "download", "downloads" -> Icons.Rounded.Download
    "dcim", "camera" -> Icons.Rounded.PhotoCamera
    "pictures", "images" -> Icons.Rounded.Image
    "music" -> Icons.Rounded.LibraryMusic
    "movies", "videos" -> Icons.Rounded.VideoLibrary
    "documents" -> Icons.Rounded.Description
    "android" -> Icons.Rounded.Android
    "notifications", "ringtones", "alarms" -> Icons.Rounded.Notifications
    "podcasts", "audiobooks" -> Icons.Rounded.Podcasts
    "screenshots" -> Icons.Rounded.Screenshot
    ".wextrash" -> Icons.Rounded.Delete
    else -> null
}

@Composable
fun FileIcon(
    item: FileItem,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    size: Dp = 42.dp,
    showThumbnail: Boolean = true,
) {
    val settings = LocalAppSettings.current
    val custom = LocalIconCustomization.current
    val dark = LocalIsDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    val key = if (item.isDirectory) "folder" else item.extension
    val override = custom.overrides[key]
    val packIcon = if (override == null) custom.pack?.resolve(key) ?: if (!item.isDirectory) custom.pack?.resolve("*") else null else null

    val folderColor = if (item.isDirectory && !item.isInArchive) custom.folderColors[item.path] else null
    // Гармонизация (HCT) недешёвая — считаем один раз на тип файла и палитру.
    val baseColor = remember(folderColor, override?.color, item.isDirectory, settings.coloredFolderIcons, item.kind, item.extension, scheme.primary) {
        when {
            folderColor != null -> Color(folderColor)
            override?.color != null -> Color(override.color)
            item.isDirectory && !settings.coloredFolderIcons -> scheme.primary
            else -> FileGlyphs.colorFor(item.kind, item.extension).harmonize(scheme.primary, matchSaturation = false)
        }
    }
    val tonal = remember(baseColor, settings.iconStyle, dark, scheme.surfaceContainerHighest) {
        iconColors(baseColor, settings.iconStyle, dark, scheme.surfaceContainerHighest, if (item.isDirectory) scheme.primary else scheme.onSurfaceVariant)
    }
    // У значков иконпака свои цвета — под ними нейтральная подложка, а не цвет типа файла
    // (кроме папок с цветной меткой — метка важнее).
    var packFailed by remember(packIcon) { mutableStateOf(false) }
    val usePack = packIcon != null && !packFailed
    val colors = if (usePack && folderColor == null) tonal.copy(container = scheme.surfaceContainerHigh, border = null) else tonal

    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "select",
    )
    val basePolygon = remember(settings.iconShape) { settings.iconShape.polygon() }
    val restShape = iconShapeFor(settings.iconShape)
    val shape: Shape = when {
        progress <= 0.001f -> restShape
        progress >= 0.999f -> remember { MorphCache.shape(selectedPolygon) }
        else -> MorphShape(remember(basePolygon) { MorphCache.morph(basePolygon, selectedPolygon) }, progress)
    }

    val thumbModel: Any? = if (showThumbnail && settings.showThumbnails && !item.isInArchive && !item.isDirectory) {
        when (item.kind) {
            FileKind.Image, FileKind.Vector, FileKind.Video -> remember(item.path, item.lastModified) { File(item.path) }
            FileKind.Apk -> if (item.extension == "apk") remember(item.path, item.lastModified) { ApkIconModel(item.path, item.lastModified) } else null
            FileKind.Audio -> remember(item.path, item.lastModified) { AudioCoverModel(item.path, item.lastModified) }
            FileKind.Pdf -> remember(item.path, item.lastModified) { client.wexmanager.core.icons.PdfThumbModel(item.path, item.lastModified) }
            FileKind.Font -> if (item.extension in setOf("ttf", "otf", "ttc")) remember(item.path, item.lastModified) { client.wexmanager.core.icons.FontThumbModel(item.path, item.lastModified) } else null
            else -> null
        }
    } else null

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        val containerColor = androidx.compose.ui.graphics.lerp(colors.container, scheme.primary, progress)
        val contentColor = androidx.compose.ui.graphics.lerp(colors.content, scheme.onPrimary, progress)
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(containerColor)
                .then(if (colors.border != null && progress < 0.5f) Modifier.border(1.5.dp, colors.border, shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            val glyphAlpha = 1f - progress
            if (glyphAlpha > 0f) {
                Box(Modifier.fillMaxSize().scale(1f - progress * 0.4f), contentAlignment = Alignment.Center) {
                    when {
                        override?.imagePath != null -> AsyncImage(
                            model = File(override.imagePath),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        usePack -> AsyncImage(
                            model = packIcon,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            onError = { packFailed = true }, // битый SVG — встроенный значок вместо пустой плитки
                            modifier = Modifier.fillMaxSize().padding(size * 0.08f),
                        )
                        else -> DefaultGlyph(item, override, contentColor, if (colors.container.alpha < 0.1f) scheme.surface else containerColor, size)
                    }
                    if (thumbModel != null) {
                        AsyncImage(
                            model = thumbModel,
                            contentDescription = null,
                            contentScale = if (item.kind == FileKind.Apk) ContentScale.Fit else ContentScale.Crop,
                            alignment = if (item.kind == FileKind.Pdf) Alignment.TopCenter else Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            AnimatedVisibility(selected, enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()), exit = scaleOut(MaterialTheme.motionScheme.fastEffectsSpec())) {
                Icon(Icons.Rounded.Check, contentDescription = "Выбрано", tint = scheme.onPrimary, modifier = Modifier.size(size * 0.5f))
            }
        }
        if (thumbModel != null && item.kind == FileKind.Video && progress < 0.5f) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(2.dp, 2.dp)
                    .size(size * 0.38f)
                    .clip(CircleShape)
                    .background(scheme.surface.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = scheme.onSurface, modifier = Modifier.size(size * 0.28f))
            }
        }
        if (item.isSymlink && progress < 0.5f) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset((-2).dp, 2.dp)
                    .size(size * 0.36f)
                    .clip(CircleShape)
                    .background(scheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = scheme.onTertiaryContainer, modifier = Modifier.size(size * 0.24f))
            }
        }
    }
}

@Composable
private fun DefaultGlyph(item: FileItem, override: IconOverride?, tint: Color, cutout: Color, size: Dp) {
    val glyph = FileGlyphs.get(override?.glyph)
    if (glyph != null) {
        Icon(glyph, null, tint = tint, modifier = Modifier.size(size * 0.54f))
        return
    }
    if (item.isDirectory) {
        val special = specialFolderGlyph(item.name)
        if (special != null) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Folder, null, tint = tint, modifier = Modifier.size(size * 0.6f))
                Icon(
                    special, null,
                    tint = cutout,
                    modifier = Modifier.size(size * 0.26f).offset(y = size * 0.03f),
                )
            }
            return
        }
        Icon(Icons.Rounded.Folder, null, tint = tint, modifier = Modifier.size(size * 0.56f))
        return
    }
    if (item.kind == FileKind.Unknown && item.extension.isNotEmpty() && item.extension.length <= 5) {
        Text(
            item.extension.uppercase(),
            color = tint,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * if (item.extension.length <= 3) 0.28f else 0.22f).sp,
            maxLines = 1,
            letterSpacing = 0.sp,
        )
        return
    }
    Icon(FileGlyphs.forFile(item.kind, item.extension), null, tint = tint, modifier = Modifier.size(size * 0.54f))
}
