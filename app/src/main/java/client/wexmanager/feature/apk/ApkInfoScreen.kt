package client.wexmanager.feature.apk

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Upgrade
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Web
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import client.wexmanager.core.apk.ApkAnalyzer
import client.wexmanager.core.apk.ApkCertificate
import client.wexmanager.core.apk.ApkComponent
import client.wexmanager.core.apk.ApkDetails
import client.wexmanager.core.apk.ApkPermission
import client.wexmanager.core.apk.PermissionLevel
import client.wexmanager.core.settings.AnimationSpeed
import client.wexmanager.core.util.FileOpener
import client.wexmanager.core.util.Format
import client.wexmanager.feature.browser.PropRow
import client.wexmanager.feature.browser.copyToSystemClipboard
import client.wexmanager.ui.components.PolygonShape
import client.wexmanager.ui.theme.LocalAppSettings
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun ApkInfoScreen(
    path: String,
    onBack: () -> Unit,
    onReveal: (String) -> Unit,
    onBrowseArchive: (String) -> Unit,
    onOpenText: (client.wexmanager.ui.navigation.Route.Editor) -> Unit,
    onOpenDex: (List<String>) -> Unit = {},
    onOpenResources: () -> Unit = {},
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val vm: ApkInfoViewModel = viewModel(key = "apk-info:$path") { ApkInfoViewModel(application, path) }
    val state by vm.state.collectAsStateWithLifecycle()
    val manifestBusy by vm.manifestBusy.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshInstalled() }

    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val details = (state as? ApkInfoState.Ready)?.details
    val titleVisible by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val motion = MaterialTheme.motionScheme

    var cloneDialog by remember { mutableStateOf(false) }
    var cloning by remember { mutableStateOf(false) }
    val openManifest: () -> Unit = {
        scope.launch {
            vm.exportManifest()
                .onSuccess(onOpenText)
                .onFailure { snackbar.showSnackbar("Не удалось декодировать манифест: ${it.message ?: it.javaClass.simpleName}") }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    AnimatedVisibility(
                        visible = titleVisible && details != null,
                        enter = fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { it / 2 },
                        exit = fadeOut(motion.fastEffectsSpec()) + slideOutVertically(motion.fastSpatialSpec()) { it / 2 },
                    ) {
                        Text(details?.label.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
                    }
                },
                actions = {
                    if (details != null) ApkMenu(details, context, onSummary = {
                        val text = vm.summaryText() ?: return@ApkMenu
                        val copied = runCatching {
                            val cm = context.getSystemService(ClipboardManager::class.java) ?: error("clipboard")
                            cm.setPrimaryClip(ClipData.newPlainText("Сводка", text))
                        }.isSuccess
                        if (copied) scope.launch { snackbar.showSnackbar("Сводка скопирована") }
                    })
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()).togetherWith(fadeOut(motion.fastEffectsSpec())) },
            label = "apkState",
        ) { s ->
            when (s) {
                ApkInfoState.Loading -> Column(
                    Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    LoadingIndicator(Modifier.size(72.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Анализ APK…", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        File(path).name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
                is ApkInfoState.Error -> ShapeMessage(
                    icon = Icons.Rounded.Error,
                    title = "Не удалось разобрать APK",
                    message = s.message,
                    error = true,
                    modifier = Modifier.padding(padding),
                ) {
                    FilledTonalButton(onClick = vm::load, shapes = ButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Повторить")
                    }
                    FilledTonalButton(onClick = { onBrowseArchive(path) }, shapes = ButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.FolderZip, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Как архив")
                    }
                }
                is ApkInfoState.Ready -> ApkInfoContent(
                    d = s.details,
                    padding = padding,
                    listState = listState,
                    manifestBusy = manifestBusy,
                    onInstall = { safeStart(context, "Не удалось открыть установщик") { FileOpener.installApk(context, File(path)) } },
                    onLaunch = { launchApp(context, s.details.packageName) },
                    onBrowse = { onBrowseArchive(path) },
                    onReveal = { onReveal(path) },
                    onManifest = openManifest,
                    onDex = { onOpenDex(s.details.dexFiles.map { it.name }) },
                    onResources = onOpenResources,
                    onClone = { cloneDialog = true },
                    cloning = cloning,
                )
            }
        }
    }
    if (cloneDialog && details != null) {
        var pkg by remember { mutableStateOf(details.packageName + ".clone") }
        val valid = client.wexmanager.core.apk.ApkCloner.isValidPackage(pkg) && pkg != details.packageName
        AlertDialog(
            onDismissRequest = { cloneDialog = false },
            icon = { Icon(Icons.Rounded.ContentCopy, null) },
            title = { Text("Клон APK") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Копия с другим именем пакета установится рядом с оригиналом. Будет подписана вашим ключом.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = pkg, onValueChange = { pkg = it.trim() },
                        label = { Text("Имя пакета") },
                        isError = !valid,
                        supportingText = if (!valid) { { Text("Например: ${details.packageName}.clone") } } else null,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            },
            confirmButton = {
                Button(enabled = valid, onClick = {
                    cloneDialog = false
                    cloning = true
                    scope.launch {
                        vm.cloneApk(pkg)
                            .onSuccess { snackbar.showSnackbar("Клон собран — подписывается: $it") }
                            .onFailure { snackbar.showSnackbar("Не удалось клонировать: ${it.message ?: it.javaClass.simpleName}") }
                        cloning = false
                    }
                }) { Text("Создать") }
            },
            dismissButton = { TextButton(onClick = { cloneDialog = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ApkMenu(d: ApkDetails, context: Context, onSummary: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.MoreVert, "Ещё") }
        DropdownMenuPopup(expanded = open, onDismissRequest = { open = false }) {
            val items = buildList<Triple<String, ImageVector, () -> Unit>> {
                add(Triple("Сводка", Icons.Rounded.ContentCopy, onSummary))
                add(Triple("Поделиться APK", Icons.Rounded.Share) { safeStart(context, "Не удалось поделиться") { FileOpener.share(context, listOf(File(d.path))) } })
                add(Triple("Копировать имя пакета", Icons.Rounded.ContentCopy) { copyToSystemClipboard(context, d.packageName) })
                add(Triple("Копировать путь", Icons.Rounded.Route) { copyToSystemClipboard(context, d.path) })
                d.icon?.let { icon ->
                    add(Triple("Сохранить иконку (PNG)", Icons.Rounded.Image) {
                        // Иконка в полном размере анализа — в «Загрузки/WexManager/<пакет>.png».
                        val msg = runCatching {
                            @Suppress("DEPRECATION")
                            val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "WexManager").apply { mkdirs() }
                            val out = File(dir, d.packageName + ".png")
                            out.outputStream().use { icon.bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                            "Иконка сохранена: Загрузки/WexManager/${out.name}"
                        }.getOrElse { "Не удалось сохранить иконку: ${it.message}" }
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                    })
                }
                if (d.installed != null) add(Triple("Сведения о приложении", Icons.Rounded.Info) { openAppDetails(context, d.packageName) })
            }
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
                items.forEachIndexed { i, (title, icon, action) ->
                    DropdownMenuItem(
                        text = { Text(title) },
                        leadingIcon = { Icon(icon, null) },
                        onClick = { open = false; action() },
                        shape = when {
                            items.size == 1 -> MenuDefaults.standaloneItemShape
                            i == 0 -> MenuDefaults.leadingItemShape
                            i == items.lastIndex -> MenuDefaults.trailingItemShape
                            else -> MenuDefaults.middleItemShape
                        },
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Содержимое

private sealed interface SigRow {
    data class Schemes(val schemes: List<String>) : SigRow
    data class Cert(val index: Int, val total: Int, val cert: ApkCertificate) : SigRow

}

@Composable
private fun ApkInfoContent(
    d: ApkDetails,
    padding: PaddingValues,
    listState: LazyListState,
    manifestBusy: Boolean,
    onInstall: () -> Unit,
    onLaunch: () -> Unit,
    onBrowse: () -> Unit,
    onReveal: () -> Unit,
    onManifest: () -> Unit,
    onDex: () -> Unit = {},
    onResources: () -> Unit = {},
    onClone: () -> Unit = {},
    cloning: Boolean = false,
) {
    val context = LocalContext.current
    val sizeUnits = LocalAppSettings.current.sizeUnits
    var expanded by rememberSaveable(
        stateSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() }),
    ) { mutableStateOf(emptySet<String>()) }
    val toggle: (String) -> Unit = { id -> expanded = if (id in expanded) expanded - id else expanded + id }
    val copy: (String) -> Unit = { copyToSystemClipboard(context, it) }

    val sigRows = remember(d) {
        buildList<SigRow> {
            add(SigRow.Schemes(d.signatureSchemes))
            d.certificates.forEachIndexed { i, c -> add(SigRow.Cert(i, d.certificates.size, c)) }
        }
    }
    val fileRows = remember(d, sizeUnits) {
        val s = d.stats
        buildList {
            add("Записей в архиве" to s.entries.toString())
            add("Размер APK" to Format.size(d.fileSize, sizeUnits))
            add("Без сжатия" to Format.size(s.uncompressedSize, sizeUnits))
            if (s.uncompressedSize > 0) add("Сжато до" to "${(d.fileSize * 100 / s.uncompressedSize).coerceAtMost(999)} % от исходного")
            add("resources.arsc" to if (s.resourcesArscSize > 0) Format.size(s.resourcesArscSize, sizeUnits) else "нет")
            add("Ресурсы res/" to Format.files(s.resCount))
            add("Ассеты assets/" to "${Format.files(s.assetsCount)} • ${Format.size(s.assetsSize, sizeUnits)}")
            add("META-INF" to Format.files(s.metaInfCount))
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "hero", contentType = "hero") { Hero(d, onCopy = copy) }
        item(key = "actions", contentType = "actions") {
            ActionButtons(d, onInstall = onInstall, onLaunch = onLaunch, onBrowse = onBrowse, onReveal = onReveal)
        }
        if (!d.parsedBySystem) {
            item(key = "warn", contentType = "warn") {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Warning, null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Система не смогла разобрать этот APK — сведения получены напрямую из манифеста и могут быть неполными.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        groupLabel("sec", "Безопасность")
        expandableSection(
            id = "perm", title = "Разрешения",
            summary = when {
                d.permissions.isEmpty() -> "Не запрашивает"
                d.dangerousCount > 0 -> "${d.permissions.size} • опасных: ${d.dangerousCount}"
                else -> "${d.permissions.size} • опасных нет"
            },
            icon = Icons.Rounded.Security, polygon = MaterialShapes.Cookie6Sided,
            items = d.permissions, expanded = "perm" in expanded, onToggle = toggle,
            warn = d.dangerousCount > 0,
        ) { p, shapes -> PermissionRow(p, shapes, onCopy = copy) }
        expandableSection(
            id = "sig", title = "Подписи",
            summary = if (d.certificates.isEmpty()) "Не подписан или подпись не прочитана"
            else "${d.certificates.size} ${Format.plural(d.certificates.size, "сертификат", "сертификата", "сертификатов")}" +
                (if (d.signatureSchemes.isNotEmpty()) " • схемы ${d.signatureSchemes.joinToString(", ")}" else ""),
            icon = Icons.Rounded.Key, polygon = MaterialShapes.Clover4Leaf,
            items = sigRows, expanded = "sig" in expanded, onToggle = toggle,
        ) { row, shapes ->
            when (row) {
                is SigRow.Schemes -> SchemesRow(row.schemes, shapes)
                is SigRow.Cert -> CertificateRow(row, shapes, onCopy = copy)
            }
        }

        groupLabel("comp", "Компоненты")
        componentSection("act", "Активности", Icons.Rounded.Web, MaterialShapes.Square, d.activities, d.packageName, expanded, toggle, copy)
        componentSection("svc", "Сервисы", Icons.Rounded.SettingsSuggest, MaterialShapes.Pentagon, d.services, d.packageName, expanded, toggle, copy)
        componentSection("rcv", "Приёмники", Icons.Rounded.Podcasts, MaterialShapes.Sunny, d.receivers, d.packageName, expanded, toggle, copy)
        componentSection("prv", "Провайдеры", Icons.Rounded.Storage, MaterialShapes.Gem, d.providers, d.packageName, expanded, toggle, copy)

        groupLabel("files", "Содержимое")
        item(key = "manifest", contentType = "manifest") {
            SegmentedListItem(
                onClick = onManifest,
                enabled = !manifestBusy,
                shapes = ListItemDefaults.segmentedShapes(0, 1),
                colors = groupHeaderColors(),
                leadingContent = {
                    ShapedIcon(
                        Icons.Rounded.Code, MaterialShapes.Cookie9Sided,
                        MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                },
                supportingContent = { Text("Расшифровать, править и собрать обратно в APK") },
                trailingContent = {
                    if (manifestBusy) LoadingIndicator(Modifier.size(28.dp))
                    else Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).animateItem(),
            ) { Text("AndroidManifest.xml") }
        }
        if (d.dexFiles.isNotEmpty()) item(key = "dexedit", contentType = "manifest") {
            SegmentedListItem(
                onClick = onDex,
                shapes = ListItemDefaults.segmentedShapes(0, 1),
                colors = groupHeaderColors(),
                leadingContent = {
                    ShapedIcon(
                        Icons.Rounded.DataObject, MaterialShapes.Arch,
                        MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                },
                supportingContent = { Text("Классы smali: править, искать, собрать обратно в APK") },
                trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).animateItem(),
            ) { Text("DEX-редактор") }
        }
        item(key = "arscedit", contentType = "manifest") {
            SegmentedListItem(
                onClick = onResources,
                shapes = ListItemDefaults.segmentedShapes(0, 1),
                colors = groupHeaderColors(),
                leadingContent = {
                    ShapedIcon(
                        Icons.Rounded.Translate, MaterialShapes.Sunny,
                        MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                },
                supportingContent = { Text("resources.arsc: строки, цвета, размеры; перевод на другие языки") },
                trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).animateItem(),
            ) { Text("Ресурсы и перевод") }
        }
        item(key = "clone", contentType = "manifest") {
            SegmentedListItem(
                onClick = onClone,
                enabled = !cloning,
                shapes = ListItemDefaults.segmentedShapes(0, 1),
                colors = groupHeaderColors(),
                leadingContent = {
                    ShapedIcon(
                        Icons.Rounded.ContentCopy, MaterialShapes.Flower,
                        MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                },
                supportingContent = { Text("Другое имя пакета — копия ставится рядом с оригиналом") },
                trailingContent = {
                    if (cloning) LoadingIndicator(Modifier.size(28.dp))
                    else Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).animateItem(),
            ) { Text("Клон APK") }
        }
        expandableSection(
            id = "abi", title = "Нативные библиотеки",
            summary = if (d.abis.isEmpty()) "Нет" else d.abis.joinToString(", ") { it.abi } + when {
                d.abis.none { it.checked16k } -> ""
                d.abis.filter { it.checked16k }.all { it.ready16k } -> " • страницы 16 КБ: да"
                else -> " • страницы 16 КБ: нет"
            },
            icon = Icons.Rounded.Memory, polygon = MaterialShapes.Cookie4Sided,
            items = d.abis, expanded = "abi" in expanded, onToggle = toggle,
        ) { abi, shapes ->
            SegmentedListItem(
                onClick = { copy(abi.libraries.joinToString("\n")) },
                shapes = shapes,
                colors = groupItemColors(),
                leadingContent = { Icon(Icons.Rounded.Memory, null) },
                supportingContent = {
                    Column {
                        Text(
                            abi.libraries.joinToString(", "),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        )
                        if (abi.checked16k) {
                            val issues = buildList {
                                if (abi.notAligned16k.isNotEmpty()) add("ELF выровнен < 16 КБ: " + abi.notAligned16k.joinToString(", "))
                                if (abi.zipNotAligned16k.isNotEmpty()) add("в APK не на границе 16 КБ: " + abi.zipNotAligned16k.joinToString(", "))
                            }
                            Text(
                                if (issues.isEmpty()) "Страницы 16 КБ: готово" else issues.joinToString("\n"),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (issues.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                maxLines = 4, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                trailingContent = {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Format.size(abi.totalSize, sizeUnits), style = MaterialTheme.typography.labelLarge)
                        Text("${abi.libraries.size} шт.", style = MaterialTheme.typography.labelSmall)
                    }
                },
            ) { Text(abi.abi) }
        }
        expandableSection(
            id = "dex", title = "DEX-файлы",
            summary = if (d.dexFiles.isEmpty()) "Нет" else
                "${d.dexFiles.size} • ${Format.size(d.dexFiles.sumOf { it.size }, sizeUnits)}",
            icon = Icons.Rounded.DataObject, polygon = MaterialShapes.Arch,
            items = d.dexFiles, expanded = "dex" in expanded, onToggle = toggle,
        ) { dex, shapes ->
            SegmentedListItem(
                onClick = { copy(dex.name) },
                shapes = shapes,
                colors = groupItemColors(),
                leadingContent = { Icon(Icons.Rounded.DataObject, null) },
                supportingContent = { Text("В архиве: ${Format.size(dex.compressedSize, sizeUnits)}") },
                trailingContent = { Text(Format.size(dex.size, sizeUnits), style = MaterialTheme.typography.labelLarge) },
            ) { Text(dex.name) }
        }
        expandableSection(
            id = "zip", title = "Файлы",
            summary = "${Format.files(d.stats.entries)} • ${Format.size(d.stats.uncompressedSize, sizeUnits)}",
            icon = Icons.Rounded.Inventory2, polygon = MaterialShapes.Bun,
            items = fileRows, expanded = "zip" in expanded, onToggle = toggle,
        ) { (label, value), shapes ->
            SegmentedListItem(
                onClick = { copy(value) },
                shapes = shapes,
                colors = groupItemColors(),
                trailingContent = { Text(value, style = MaterialTheme.typography.labelLarge) },
            ) { Text(label, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

// ------------------------------------------------------------------ Шапка

@Composable
private fun Hero(d: ApkDetails, onCopy: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val s = LocalAppSettings.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(164.dp), contentAlignment = Alignment.Center) {
            val bgShape = remember { PolygonShape(MaterialShapes.Cookie9Sided) }
            val iconShape = remember { PolygonShape(MaterialShapes.Square) }
            val animate = s.animationSpeed != AnimationSpeed.Off
            val rotation = if (animate) {
                val transition = rememberInfiniteTransition(label = "heroSpin")
                transition.animateFloat(0f, 360f, infiniteRepeatable(tween(48_000, easing = LinearEasing)), label = "rot")
            } else null
            Box(
                Modifier
                    .size(164.dp)
                    .graphicsLayer { rotationZ = rotation?.value ?: 0f }
                    .clip(bgShape)
                    .background(scheme.primaryContainer),
            )
            val image = remember(d.icon) { d.icon?.bitmap?.asImageBitmap() }
            AppIconImage(image, adaptive = d.icon?.adaptive == true, size = 104.dp, shape = iconShape)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            d.label,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (d.packageName.isNotEmpty()) {
            Text(
                d.packageName,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onCopy(d.packageName) }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            d.versionName?.takeIf { it.isNotBlank() }?.let { InfoPill(it, icon = Icons.Rounded.Tag) }
            InfoPill("код ${d.versionCode}", icon = Icons.Rounded.Numbers)
            InfoPill(Format.size(d.fileSize, s.sizeUnits), icon = Icons.Rounded.SdStorage)
            if (d.debuggable) InfoPill(
                "Отладочная", icon = Icons.Rounded.BugReport,
                container = scheme.tertiaryContainer, content = scheme.onTertiaryContainer,
            )
            d.splitName?.let { InfoPill("split: $it", icon = Icons.AutoMirrored.Rounded.CallSplit) }
        }
        d.installed?.let { inst ->
            Spacer(Modifier.height(10.dp))
            val cmp = d.versionComparison ?: 0
            val ver = inst.versionName?.takeIf { it.isNotBlank() } ?: inst.versionCode.toString()
            val (text, icon) = when {
                cmp > 0 -> "Установлено: $ver — этот APK новее" to Icons.Rounded.Upgrade
                cmp < 0 -> "Установлено: $ver — этот APK старше" to Icons.Rounded.History
                else -> "Установлено: $ver — та же версия" to Icons.Rounded.CheckCircle
            }
            InfoPill(
                text, icon = icon,
                container = if (cmp > 0) scheme.primary else if (cmp < 0) scheme.errorContainer else scheme.surfaceContainerHighest,
                content = if (cmp > 0) scheme.onPrimary else if (cmp < 0) scheme.onErrorContainer else scheme.onSurface,
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SdkTile("Мин. SDK", d.minSdk, incompatible = d.minSdk > Build.VERSION.SDK_INT, modifier = Modifier.weight(1f))
            SdkTile("Целевой SDK", d.targetSdk, modifier = Modifier.weight(1f))
            SdkTile("Сборка", d.compileSdk, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SdkTile(title: String, api: Int, modifier: Modifier = Modifier, incompatible: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = if (incompatible) scheme.errorContainer else scheme.surfaceContainerHigh,
        contentColor = if (incompatible) scheme.onErrorContainer else scheme.onSurface,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = if (incompatible) scheme.onErrorContainer else scheme.onSurfaceVariant, maxLines = 1)
            Text(
                if (api > 0) api.toString() else "—",
                style = MaterialTheme.typography.headlineMedium,
                color = if (incompatible) scheme.onErrorContainer else scheme.primary,
            )
            Text(
                when {
                    incompatible -> "Не для этого устройства"
                    api > 0 -> "Android ${ApkAnalyzer.androidVersion(api)}"
                    else -> "Неизвестно"
                },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ActionButtons(
    d: ApkDetails,
    onInstall: () -> Unit,
    onLaunch: () -> Unit,
    onBrowse: () -> Unit,
    onReveal: () -> Unit,
) {
    val big = ButtonDefaults.MediumContainerHeight
    val bigStyle = ButtonDefaults.textStyleFor(big)
    // Подписи ужимаются шрифтом, если не помещаются (узкий экран, крупный системный шрифт).
    val bigAutoSize = remember(bigStyle) { buttonAutoSize(bigStyle.fontSize, 16.sp) }
    val smallAutoSize = remember { buttonAutoSize(TextUnit.Unspecified, 14.sp) }
    val cmp = d.versionComparison
    val installLabel = when {
        cmp == null -> "Установить"
        cmp > 0 -> "Обновить"
        cmp == 0 -> "Переустановить"
        else -> "Установить"
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onInstall,
                shapes = ButtonDefaults.shapesFor(big),
                contentPadding = ButtonDefaults.contentPaddingFor(big),
                modifier = Modifier.weight(1f).height(big),
            ) {
                Icon(if (cmp != null && cmp > 0) Icons.Rounded.Upgrade else Icons.Rounded.InstallMobile, null, Modifier.size(ButtonDefaults.iconSizeFor(big)))
                Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(big)))
                Text(installLabel, style = bigStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, autoSize = bigAutoSize)
            }
            if (d.installed?.launchable == true) {
                // «Открыть» — по ширине содержимого, всё остальное место у основной кнопки
                // (иначе «Переустановить» обрезается на ширине ~411dp).
                FilledTonalButton(
                    onClick = onLaunch,
                    shapes = ButtonDefaults.shapesFor(big),
                    contentPadding = ButtonDefaults.contentPaddingFor(big),
                    modifier = Modifier.height(big),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(ButtonDefaults.iconSizeFor(big)))
                    Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(big)))
                    Text("Открыть", style = bigStyle, maxLines = 1)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onBrowse, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.FolderZip, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Содержимое", maxLines = 1, overflow = TextOverflow.Ellipsis, autoSize = smallAutoSize)
            }
            FilledTonalButton(onClick = onReveal, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.FolderOpen, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("В папке", maxLines = 1, overflow = TextOverflow.Ellipsis, autoSize = smallAutoSize)
            }
        }
    }
}

/** Автоподбор размера шрифта подписи кнопки: от [max] (или [fallback]) вниз до 11sp. */
private fun buttonAutoSize(max: TextUnit, fallback: TextUnit): TextAutoSize {
    val top = if (max.isSp && max.value >= 12f) max else fallback
    return TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = top, stepSize = 0.5.sp)
}

// ------------------------------------------------------------------ Секции

private fun LazyListScope.groupLabel(key: String, text: String) {
    item(key = "label:$key", contentType = "label") {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.animateItem().padding(start = 32.dp, end = 32.dp, top = 18.dp, bottom = 6.dp),
        )
    }
}

/** Разворачиваемая секция: заголовок + элементы, вместе образующие одну сегментированную группу. */
private fun <T> LazyListScope.expandableSection(
    id: String,
    title: String,
    summary: String,
    icon: ImageVector,
    polygon: RoundedPolygon,
    items: List<T>,
    expanded: Boolean,
    onToggle: (String) -> Unit,
    warn: Boolean = false,
    content: @Composable (item: T, shapes: ListItemShapes) -> Unit,
) {
    val open = expanded && items.isNotEmpty()
    val count = if (open) items.size + 1 else 1
    item(key = "head:$id", contentType = "sectionHeader") {
        val motion = MaterialTheme.motionScheme
        val scheme = MaterialTheme.colorScheme
        val rotation by animateFloatAsState(if (open) 180f else 0f, motion.defaultSpatialSpec(), label = "chevron")
        SegmentedListItem(
            onClick = { onToggle(id) },
            enabled = items.isNotEmpty(),
            shapes = ListItemDefaults.segmentedShapes(0, count),
            colors = groupHeaderColors(),
            leadingContent = {
                ShapedIcon(
                    icon, polygon,
                    if (warn) scheme.errorContainer else scheme.secondaryContainer,
                    if (warn) scheme.onErrorContainer else scheme.onSecondaryContainer,
                )
            },
            supportingContent = { Text(summary, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            trailingContent = {
                if (items.isNotEmpty()) Icon(Icons.Rounded.ExpandMore, if (open) "Свернуть" else "Развернуть", Modifier.rotate(rotation))
            },
            modifier = Modifier
                .animateItem(
                    fadeInSpec = motion.defaultEffectsSpec(),
                    placementSpec = motion.defaultSpatialSpec(),
                    fadeOutSpec = motion.fastEffectsSpec(),
                )
                .padding(horizontal = 16.dp)
                .padding(top = 6.dp),
        ) { Text(title) }
    }
    if (open) {
        itemsIndexed(items, key = { i, _ -> "item:$id:$i" }, contentType = { _, _ -> "row:$id" }) { i, item ->
            val motion = MaterialTheme.motionScheme
            Box(
                Modifier
                    .animateItem(
                        fadeInSpec = motion.defaultEffectsSpec(),
                        placementSpec = motion.defaultSpatialSpec(),
                        fadeOutSpec = motion.fastEffectsSpec(),
                    )
                    .padding(horizontal = 16.dp)
                    .padding(top = ListItemDefaults.SegmentedGap),
            ) {
                content(item, ListItemDefaults.segmentedShapes(i + 1, count))
            }
        }
    }
}

private fun LazyListScope.componentSection(
    id: String,
    title: String,
    icon: ImageVector,
    polygon: RoundedPolygon,
    items: List<ApkComponent>,
    pkg: String,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    onCopy: (String) -> Unit,
) {
    val exported = items.count { it.exported }
    expandableSection(
        id = id, title = title,
        summary = if (items.isEmpty()) "Нет" else "${items.size} • экспортировано: $exported",
        icon = icon, polygon = polygon,
        items = items, expanded = id in expanded, onToggle = onToggle,
    ) { c, shapes -> ComponentRow(c, pkg, icon, shapes, onCopy) }
}

@Composable
private fun PermissionRow(p: ApkPermission, shapes: ListItemShapes, onCopy: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (icon, tint) = when (p.level) {
        PermissionLevel.Dangerous -> Icons.Rounded.Warning to scheme.error
        PermissionLevel.Signature -> Icons.Rounded.VerifiedUser to scheme.tertiary
        PermissionLevel.Normal -> Icons.Rounded.Shield to scheme.primary
        PermissionLevel.Unknown -> Icons.AutoMirrored.Rounded.HelpOutline to scheme.outline
    }
    val short = p.name.removePrefix("android.permission.")
    val lines = buildList {
        p.label?.let { add(it.replaceFirstChar { c -> c.uppercase() }) }
        p.description?.let { add(it) }
        if (isEmpty() && p.level == PermissionLevel.Unknown) add("Не объявлено в системе")
    }
    val badge = when (p.level) {
        PermissionLevel.Dangerous -> Triple("опасное", scheme.errorContainer, scheme.onErrorContainer)
        PermissionLevel.Signature -> Triple("системное", scheme.tertiaryContainer, scheme.onTertiaryContainer)
        else -> null
    }
    SegmentedListItem(
        onClick = { onCopy(p.name) },
        shapes = shapes,
        colors = groupItemColors(),
        leadingContent = { Icon(icon, null, tint = tint) },
        supportingContent = lines.takeIf { it.isNotEmpty() }?.let {
            { Text(it.joinToString("\n"), maxLines = 3, overflow = TextOverflow.Ellipsis) }
        },
        trailingContent = badge?.let { (text, bg, fg) -> { TinyBadge(text, bg, fg) } },
    ) {
        Text(short, maxLines = 2, overflow = TextOverflow.MiddleEllipsis, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ComponentRow(c: ApkComponent, pkg: String, icon: ImageVector, shapes: ListItemShapes, onCopy: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val simple = c.name.substringAfterLast('.')
    val shown = if (pkg.isNotEmpty() && c.name.startsWith("$pkg.")) "." + c.name.removePrefix("$pkg.") else c.name
    SegmentedListItem(
        onClick = { onCopy(c.name) },
        shapes = shapes,
        colors = groupItemColors(),
        leadingContent = { Icon(icon, null, tint = if (c.launcher) scheme.primary else scheme.onSurfaceVariant) },
        supportingContent = {
            Column {
                Text(
                    shown,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 2,
                    overflow = TextOverflow.MiddleEllipsis,
                )
                c.extra?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        trailingContent = c.takeIf { it.launcher || it.exported }?.let { comp ->
            {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (comp.launcher) TinyBadge("главная", scheme.primaryContainer, scheme.onPrimaryContainer)
                    if (comp.exported) TinyBadge("exported", scheme.tertiaryContainer, scheme.onTertiaryContainer)
                }
            }
        },
    ) { Text(simple, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun SchemesRow(schemes: List<String>, shapes: ListItemShapes) {
    val scheme = MaterialTheme.colorScheme
    SegmentedListItem(
        shapes = shapes,
        colors = groupItemColors(),
        leadingContent = { Icon(Icons.Rounded.VerifiedUser, null) },
        supportingContent = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                listOf("v1", "v2", "v3", "v3.1", "v4").forEach { v ->
                    val on = v in schemes
                    InfoPill(
                        v,
                        icon = if (on) Icons.Rounded.CheckCircle else null,
                        container = if (on) scheme.primaryContainer else scheme.surfaceContainerHighest,
                        content = if (on) scheme.onPrimaryContainer else scheme.outline,
                    )
                }
            }
        },
    ) { Text("Схемы подписи") }
}

private val certDate = ThreadLocal.withInitial { SimpleDateFormat("d MMM yyyy", Format.RU) }

@Composable
private fun CertificateRow(row: SigRow.Cert, shapes: ListItemShapes, onCopy: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val c = row.cert
    val now = System.currentTimeMillis()
    val valid = now in c.notBefore..c.notAfter
    SegmentedListItem(
        shapes = shapes,
        colors = groupItemColors(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        row.total == 1 -> "Сертификат"
                        row.index == row.total - 1 -> "Сертификат ${row.index + 1} (текущий)"
                        else -> "Сертификат ${row.index + 1}"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TinyBadge(
                    if (valid) "действителен" else if (now > c.notAfter) "истёк" else "ещё не действует",
                    if (valid) scheme.primaryContainer else scheme.errorContainer,
                    if (valid) scheme.onPrimaryContainer else scheme.onErrorContainer,
                )
            }
            Spacer(Modifier.height(6.dp))
            PropRow("Субъект", formatDn(c.subject), copy = true)
            PropRow("Издатель", formatDn(c.issuer), copy = true)
            PropRow("Действует", "${certDate.get()!!.format(Date(c.notBefore))} — ${certDate.get()!!.format(Date(c.notAfter))}")
            PropRow("Серийный №", c.serial, copy = true, mono = true)
            PropRow("Алгоритм", c.signatureAlgorithm)
            PropRow("Ключ", c.publicKey)
            Spacer(Modifier.height(4.dp))
            listOf("MD5" to c.md5, "SHA-1" to c.sha1, "SHA-256" to c.sha256).forEach { (alg, hash) ->
                Fingerprint(alg, hash, onCopy)
            }
        }
    }
}

@Composable
private fun Fingerprint(alg: String, hash: String, onCopy: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable { onCopy(hash) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(alg, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(hash, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Rounded.ContentCopy, "Копировать", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** «CN=Имя,O=Орг,C=RU» → по строке на компонент (экранированные запятые сохраняются). */
private fun formatDn(dn: String): String = dn.split(dnSeparator).joinToString("\n") { it.trim() }

private val dnSeparator = Regex("(?<!\\\\),(?=\\s*[A-Za-z0-9.]+=)")

// ------------------------------------------------------------------ Действия

/** Запуск системного экрана: SecurityException/IllegalArgumentException не должны ронять приложение. */
private inline fun safeStart(context: Context, error: String, block: () -> Unit) {
    try {
        block()
    } catch (e: RuntimeException) {
        Toast.makeText(context, "$error: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_SHORT).show()
    }
}

internal fun launchApp(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent == null) {
        Toast.makeText(context, "У приложения нет экрана запуска", Toast.LENGTH_SHORT).show()
        return
    }
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Toast.makeText(context, "Не удалось запустить: ${it.message}", Toast.LENGTH_SHORT).show() }
}

internal fun openAppDetails(context: Context, packageName: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "Не удалось открыть настройки", Toast.LENGTH_SHORT).show() }
}
