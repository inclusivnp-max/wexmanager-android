package client.wexmanager.feature.tools

import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Interests
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.SwipeRight
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import client.wexmanager.BuildConfig
import client.wexmanager.ui.components.MorphShape
import client.wexmanager.ui.components.PolygonShape
import client.wexmanager.ui.theme.LocalAppSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ Данные

@Immutable
private data class FeatureInfo(val icon: ImageVector, val title: String, val text: String, val shape: RoundedPolygon)

private val Features: List<FeatureInfo>
    get() = listOf(
        FeatureInfo(Icons.Rounded.ViewColumn, "Две панели", "Копирование и перемещение между панелями в одно касание", MaterialShapes.Cookie9Sided),
        FeatureInfo(Icons.Rounded.SwipeRight, "Жесты выделения", "Проведите по иконкам, чтобы выделить сразу диапазон файлов", MaterialShapes.Clover4Leaf),
        FeatureInfo(Icons.Rounded.FolderZip, "Архивы ZIP, 7z, TAR", "Просмотр, распаковка и создание архивов прямо в панели", MaterialShapes.Cookie6Sided),
        FeatureInfo(Icons.Rounded.Code, "Редактор кода", "Подсветка синтаксиса, нумерация строк, поиск и замена", MaterialShapes.Pentagon),
        FeatureInfo(Icons.Rounded.Android, "Просмотр APK", "Версия, разрешения, подписи и установка пакетов", MaterialShapes.Gem),
        FeatureInfo(Icons.Rounded.DonutLarge, "Анализатор памяти", "Что занимает место: самые крупные папки и файлы", MaterialShapes.Sunny),
        FeatureInfo(Icons.Rounded.Delete, "Корзина", "Случайно удалённое можно восстановить", MaterialShapes.Puffy),
        FeatureInfo(Icons.Rounded.Interests, "Иконпаки", "Иконки из сторонних пакетов и свои значки для типов файлов", MaterialShapes.Flower),
        FeatureInfo(Icons.Rounded.Palette, "Темы Material You", "Динамические цвета, палитры, формы и тёмная тема", MaterialShapes.SoftBurst),
        FeatureInfo(Icons.Rounded.Lan, "Сетевые хранилища", "SFTP, FTP, FTPS и SMB с поиском общих папок: просмотр, правка, загрузка", MaterialShapes.Cookie4Sided),
        FeatureInfo(Icons.Rounded.AdminPanelSettings, "Root-доступ", "Просмотр /data и системных разделов (только чтение)", MaterialShapes.Slanted),
        FeatureInfo(Icons.Rounded.PictureAsPdf, "PDF и шрифты", "Встроенный просмотр документов и шрифтов с миниатюрами", MaterialShapes.Arch),
        FeatureInfo(Icons.Rounded.Verified, "Правка и подпись APK", "AndroidManifest.xml прямо в APK, подпись своим ключом: PKCS12, BKS или pk8", MaterialShapes.Ghostish),
        FeatureInfo(Icons.Rounded.AutoFixHigh, "Инструменты текста", "Сортировка, JSON, Base64, URL, проверка синтаксиса и сравнение правок", MaterialShapes.Bun),
        FeatureInfo(Icons.Rounded.CleaningServices, "Очистка памяти", "Временные файлы, кеш миниатюр и уже установленные APK", MaterialShapes.Flower),
    )

@Immutable
private data class TechInfo(val icon: ImageVector, val name: String)

private val Technologies: List<TechInfo>
    get() = listOf(
        TechInfo(Icons.Rounded.Layers, "Jetpack Compose"),
        TechInfo(Icons.Rounded.AutoAwesome, "Material 3 Expressive"),
        TechInfo(Icons.Rounded.Explore, "Navigation 3"),
        TechInfo(Icons.Rounded.Image, "Coil 3"),
        TechInfo(Icons.Rounded.Movie, "Media3"),
        TechInfo(Icons.Rounded.Code, "Sora Editor"),
        TechInfo(Icons.Rounded.Inventory2, "Apache Commons Compress"),
        TechInfo(Icons.Rounded.ColorLens, "MaterialKolor"),
        TechInfo(Icons.Rounded.ZoomIn, "Telephoto"),
        TechInfo(Icons.Rounded.Bolt, "Kotlin Coroutines"),
    )

private enum class License(val label: String) {
    Apache2("Apache 2.0"),
    Mit("MIT"),
    Lgpl21("LGPL 2.1"),
    Bsd0("0BSD"),
    Bsd2("BSD 2-Clause"),
    Bsd3("BSD 3-Clause"),
    Epl2("EPL 2.0"),
    UnRar("UnRAR"),
    Ofl("OFL 1.1"),
}

@Immutable
private data class LibraryInfo(val name: String, val author: String, val license: License, val url: String)

private val Libraries = listOf(
    LibraryInfo("Jetpack Compose", "The Android Open Source Project", License.Apache2, "https://developer.android.com/jetpack/compose"),
    LibraryInfo("Material 3 для Compose", "The Android Open Source Project", License.Apache2, "https://m3.material.io"),
    LibraryInfo("Material Icons", "Google", License.Apache2, "https://fonts.google.com/icons"),
    LibraryInfo("Navigation 3", "The Android Open Source Project", License.Apache2, "https://developer.android.com/guide/navigation/navigation-3"),
    LibraryInfo("AndroidX: Lifecycle, DataStore, Graphics Shapes", "The Android Open Source Project", License.Apache2, "https://github.com/androidx/androidx"),
    LibraryInfo("Media3 ExoPlayer", "The Android Open Source Project", License.Apache2, "https://github.com/androidx/media"),
    LibraryInfo("Kotlin Coroutines", "JetBrains", License.Apache2, "https://github.com/Kotlin/kotlinx.coroutines"),
    LibraryInfo("Kotlin Serialization", "JetBrains", License.Apache2, "https://github.com/Kotlin/kotlinx.serialization"),
    LibraryInfo("Coil 3", "Coil Contributors", License.Apache2, "https://github.com/coil-kt/coil"),
    LibraryInfo("Telephoto", "Saket Narayan", License.Apache2, "https://github.com/saket/telephoto"),
    LibraryInfo("Apache Commons Compress", "The Apache Software Foundation", License.Apache2, "https://commons.apache.org/proper/commons-compress/"),
    LibraryInfo("XZ for Java", "Lasse Collin, Tukaani Project", License.Bsd0, "https://tukaani.org/xz/java.html"),
    LibraryInfo("Sora Editor", "Rosemoe", License.Lgpl21, "https://github.com/Rosemoe/sora-editor"),
    LibraryInfo("MaterialKolor", "Jordon de Hoog", License.Mit, "https://github.com/jordond/MaterialKolor"),
    LibraryInfo("TextMate для Android (tm4e)", "Eclipse Foundation", License.Epl2, "https://github.com/eclipse/tm4e"),
    LibraryInfo("Joni и JCodings", "JRuby Team", License.Mit, "https://github.com/jruby/joni"),
    LibraryInfo("JSch (форк mwiede)", "Atsuhiko Yamanaka, Matthias Wiedemann", License.Bsd3, "https://github.com/mwiede/jsch"),
    LibraryInfo("Apache Commons Net", "The Apache Software Foundation", License.Apache2, "https://commons.apache.org/proper/commons-net/"),
    LibraryInfo("SMBJ", "Hierynomus", License.Apache2, "https://github.com/hierynomus/smbj"),
    LibraryInfo("Bouncy Castle", "Legion of the Bouncy Castle", License.Mit, "https://www.bouncycastle.org"),
    LibraryInfo("MBassador", "Benjamin Diedrichsen", License.Mit, "https://github.com/bennidi/mbassador"),
    LibraryInfo("SLF4J API", "QOS.ch", License.Mit, "https://www.slf4j.org"),
    LibraryInfo("Junrar", "Junrar contributors (на основе UnRAR, Alexander Roshal)", License.UnRar, "https://github.com/junrar/junrar"),
    LibraryInfo("UnRAR (RAR5)", "Alexander Roshal, RARLAB — исходный код UnRAR нельзя использовать для создания RAR-совместимого архиватора", License.UnRar, "https://www.rarlab.com/rar_add.htm"),
    LibraryInfo("ripgrep (поиск по содержимому)", "Andrew Gallant", License.Mit, "https://github.com/BurntSushi/ripgrep"),
    LibraryInfo("jadx (DEX → Java)", "Skylot и участники", License.Apache2, "https://github.com/skylot/jadx"),
    LibraryInfo("Gson", "Google", License.Apache2, "https://github.com/google/gson"),
    LibraryInfo("OkHttp и Okio (WebDAV)", "Square", License.Apache2, "https://square.github.io/okhttp/"),
    LibraryInfo("commonmark-java (Markdown)", "Atlassian и участники", License.Bsd2, "https://github.com/commonmark/commonmark-java"),
    LibraryInfo("Zip4j", "Srikanth Reddy Lingala", License.Apache2, "https://github.com/srikanth-lingala/zip4j"),
    LibraryInfo("apksig", "The Android Open Source Project", License.Apache2, "https://android.googlesource.com/platform/tools/apksig"),
    LibraryInfo("AndroidX ProfileInstaller", "The Android Open Source Project", License.Apache2, "https://developer.android.com/jetpack/androidx/releases/profileinstaller"),
    LibraryInfo("Material Icon Theme (каталог значков)", "Philipp Kief и участники", License.Mit, "https://github.com/material-extensions/vscode-material-icon-theme"),
    LibraryInfo("Catppuccin Icons (каталог значков)", "Catppuccin", License.Mit, "https://github.com/catppuccin/vscode-icons"),
    LibraryInfo("JetBrains Mono (шрифт редактора)", "JetBrains", License.Ofl, "https://www.jetbrains.com/lp/mono/"),
    LibraryInfo("LeakCanary (отладочная сборка)", "Square", License.Apache2, "https://github.com/square/leakcanary"),
)

/** Спокойный цикл логотипа. */
private val CalmShapes: List<RoundedPolygon>
    get() = listOf(
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Clover8Leaf,
        MaterialShapes.SoftBurst,
        MaterialShapes.Cookie12Sided,
        MaterialShapes.Flower,
        MaterialShapes.Puffy,
    )

/** Все фигуры Material Shapes — для пасхалки. */
private val AllShapes: List<RoundedPolygon>
    get() = with(MaterialShapes) {
        listOf(
            Circle, Square, Slanted, Arch, Fan, Arrow, SemiCircle, Oval, Pill, Triangle, Diamond, ClamShell,
            Pentagon, Gem, Sunny, VerySunny, Cookie4Sided, Cookie6Sided, Cookie7Sided, Cookie9Sided,
            Cookie12Sided, Ghostish, Clover4Leaf, Clover8Leaf, Burst, SoftBurst, Boom, SoftBoom, Flower,
            Puffy, PuffyDiamond, PixelCircle, PixelTriangle, Bun, Heart,
        )
    }

private const val TapsToUnlock = 7

// ------------------------------------------------------------------ Экран

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val settings = LocalAppSettings.current
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val showTitle by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var party by remember { mutableStateOf(false) }
    var taps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0L) }

    val onLogoTap: () -> Unit = {
        val now = SystemClock.uptimeMillis()
        taps = if (now - lastTap > 1500) 1 else taps + 1
        lastTap = now
        if (taps >= TapsToUnlock) {
            taps = 0
            if (settings.haptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            party = !party
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                if (party) {
                    val result = snackbar.showSnackbar(
                        message = "Вы нашли пасхалку! Все ${AllShapes.size} фигур Material 3 Expressive в деле",
                        actionLabel = "Хватит",
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) party = false
                }
            }
        } else {
            if (settings.haptics) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (!party && taps >= 4) {
                val left = TapsToUnlock - taps
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar("Ещё $left…", duration = SnackbarDuration.Short)
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    AnimatedVisibility(
                        visible = showTitle,
                        enter = fadeIn(motion.fastEffectsSpec()),
                        exit = fadeOut(motion.fastEffectsSpec()),
                    ) { Text("О приложении") }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.surface,
                    scrolledContainerColor = scheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = scheme.surface,
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "hero") { Hero(party = party, onLogoTap = onLogoTap) }
            item(key = "features") {
                SectionCard(title = "Возможности") { FeatureList() }
            }
            item(key = "tech") {
                SectionCard(title = "Технологии") { TechnologyChips() }
            }
            item(key = "licenses") { LicensesCard() }
            item(key = "footer") { Footer() }
        }
    }
}

// ------------------------------------------------------------------ Шапка с логотипом

@Composable
private fun Hero(party: Boolean, onLogoTap: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedLogo(party = party, onTap = onLogoTap)
        Spacer(Modifier.height(20.dp))
        Text("WexManager", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = CircleShape, color = scheme.secondaryContainer) {
                Text(
                    "Версия ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
            if (BuildConfig.DEBUG) {
                Surface(shape = CircleShape, color = scheme.tertiaryContainer) {
                    Text(
                        "debug",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Архитектуры: ${Build.SUPPORTED_ABIS.joinToString()} · Android ${Build.VERSION.RELEASE}",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Мощный двухпанельный файловый менеджер в стиле Material 3 Expressive",
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp),
        )
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * Логотип: фигура перетекает между формами Material Shapes и медленно вращается.
 * Анимируемые значения читаются только в graphicsLayer — без рекомпозиции на каждый кадр.
 */
@Composable
private fun AnimatedLogo(party: Boolean, onTap: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme

    // Текущий переход: morph(from → to) с прогрессом [progress].
    var morph by remember { mutableStateOf(Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Clover8Leaf)) }
    var target by remember { mutableStateOf<RoundedPolygon>(MaterialShapes.Clover8Leaf) }
    var progress by remember { mutableFloatStateOf(0f) }
    var steps by remember { mutableIntStateOf(0) }
    val calmSpec = motion.slowSpatialSpec<Float>()

    LaunchedEffect(party) {
        val sequence = if (party) AllShapes else CalmShapes
        var index = sequence.indexOf(target).coerceAtLeast(0)
        while (true) {
            animate(
                initialValue = progress,
                targetValue = 1f,
                animationSpec = if (party) tween(260, easing = FastOutSlowInEasing) else calmSpec,
            ) { value, _ -> progress = value }
            if (!party) delay(1600)
            index = (index + 1) % sequence.size
            val from = target
            val next = sequence[index].takeIf { it !== from } ?: sequence[(index + 1) % sequence.size]
            // Без приостановок между записями — кадр увидит согласованное состояние.
            morph = Morph(from, next)
            target = next
            progress = 0f
            steps++
        }
    }

    // Вращение: скорость плавно меняется, угол накапливается покадрово.
    val speed = animateFloatAsState(if (party) 540f else 16f, tween(900), label = "logoSpeed")
    var rotation by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                rotation = (rotation + speed.value * (now - last) / 1_000_000_000f) % 720f
                last = now
            }
        }
    }

    val palette = listOf(
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.primary to scheme.onPrimary,
        scheme.tertiary to scheme.onTertiary,
    )
    val pair = if (party) palette[steps % palette.size] else palette[0]
    val container by animateColorAsState(pair.first, tween(if (party) 240 else 600), label = "logoColor")
    val content by animateColorAsState(pair.second, tween(if (party) 240 else 600), label = "logoContent")
    val backdrop = remember { PolygonShape(MaterialShapes.Cookie12Sided) }

    val bounce = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }

    Box(
        Modifier
            .size(212.dp)
            .graphicsLayer {
                scaleX = bounce.value
                scaleY = bounce.value
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Image,
                onClickLabel = "Логотип",
            ) {
                onTap()
                scope.launch {
                    bounce.animateTo(0.88f, spring(stiffness = Spring.StiffnessHigh))
                    bounce.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Подложка вращается в обратную сторону.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = -rotation * 0.5f
                    shape = backdrop
                    clip = true
                }
                .background(scheme.surfaceContainerHigh),
        )
        Box(
            Modifier
                .size(152.dp)
                .graphicsLayer {
                    rotationZ = rotation
                    shape = MorphShape(morph, progress)
                    clip = true
                }
                .background(container),
        )
        AnimatedContent(
            targetState = party,
            transitionSpec = {
                (scaleIn(motion.fastSpatialSpec(), initialScale = 0.5f) + fadeIn(motion.fastEffectsSpec()))
                    .togetherWith(scaleOut(motion.fastSpatialSpec(), targetScale = 0.5f) + fadeOut(motion.fastEffectsSpec()))
            },
            label = "logoIcon",
        ) { p ->
            Icon(
                if (p) Icons.Rounded.Celebration else Icons.Rounded.Folder,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(68.dp),
            )
        }
    }
}

// ------------------------------------------------------------------ Карточки

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, bottom = 10.dp, top = 8.dp),
        )
        content()
    }
}

@Composable
private fun FeatureList() {
    val scheme = MaterialTheme.colorScheme
    val features = remember { Features }
    val tones = listOf(
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
    )
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        features.forEachIndexed { i, f ->
            val (bg, fg) = tones[i % tones.size]
            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(i, features.size),
                leadingContent = { ShapeBadge(f.shape, f.icon, bg, fg, size = 44.dp, iconSize = 22.dp) },
                supportingContent = { Text(f.text) },
            ) { Text(f.title) }
        }
    }
}

@Composable
private fun TechnologyChips() {
    val scheme = MaterialTheme.colorScheme
    val tech = remember { Technologies }
    Surface(shape = RoundedCornerShape(28.dp), color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            tech.forEach { t ->
                Surface(shape = CircleShape, color = scheme.secondaryContainer) {
                    Row(
                        Modifier.padding(start = 10.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(t.icon, null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(t.name, style = MaterialTheme.typography.labelLarge, color = scheme.onSecondaryContainer)
                    }
                }
            }
        }
    }
}

@Composable
private fun LicensesCard() {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val uriHandler = LocalUriHandler.current
    var expanded by remember { mutableStateOf(false) }
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, motion.defaultSpatialSpec(), label = "licArrow")
    val summary = remember { Libraries.map { it.license.label }.distinct().joinToString(", ") }

    SectionCard(title = "Лицензии открытых библиотек") {
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            SegmentedListItem(
                onClick = { expanded = !expanded },
                shapes = ListItemDefaults.segmentedShapes(0, if (expanded) Libraries.size + 1 else 1),
                leadingContent = {
                    ShapeBadge(MaterialShapes.Cookie7Sided, Icons.Rounded.Gavel, scheme.tertiaryContainer, scheme.onTertiaryContainer, size = 44.dp, iconSize = 22.dp)
                },
                trailingContent = {
                    Icon(
                        Icons.Rounded.ExpandMore,
                        if (expanded) "Свернуть" else "Развернуть",
                        modifier = Modifier.graphicsLayer { rotationZ = arrow },
                    )
                },
                supportingContent = { Text("${Libraries.size} библиотек  •  $summary") },
            ) { Text(if (expanded) "Скрыть список" else "Показать список") }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    Libraries.forEachIndexed { i, lib ->
                        SegmentedListItem(
                            onClick = { runCatching { uriHandler.openUri(lib.url) } },
                            shapes = ListItemDefaults.segmentedShapes(i + 1, Libraries.size + 1),
                            supportingContent = { Text(lib.author) },
                            trailingContent = { LicenseBadge(lib.license) },
                        ) { Text(lib.name) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LicenseBadge(license: License) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (license) {
        License.Apache2 -> scheme.primaryContainer to scheme.onPrimaryContainer
        License.Mit -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        License.Lgpl21 -> scheme.secondaryContainer to scheme.onSecondaryContainer
        License.Bsd0, License.Bsd2, License.Bsd3 -> scheme.surfaceContainerHighest to scheme.onSurface
        License.Epl2, License.Ofl -> scheme.secondaryContainer to scheme.onSecondaryContainer
        License.UnRar -> scheme.errorContainer to scheme.onErrorContainer
    }
    Box(Modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(license.label, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

@Composable
private fun Footer() {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Сделано на Kotlin и Jetpack Compose",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Text(
            "Подсказка: логотип умеет больше, чем кажется",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.outline.copy(alpha = 0.7f),
        )
    }
}
