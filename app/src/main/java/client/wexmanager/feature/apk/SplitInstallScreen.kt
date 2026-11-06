package client.wexmanager.feature.apk

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import client.wexmanager.core.apk.ApkAnalyzer
import client.wexmanager.core.apk.ApkDetails
import client.wexmanager.core.apk.SplitApk
import client.wexmanager.core.apk.SplitBundle
import client.wexmanager.core.apk.SplitGroup
import client.wexmanager.core.apk.SplitInstaller
import client.wexmanager.core.apk.SplitMerger
import client.wexmanager.appContainer
import client.wexmanager.core.util.Format
import client.wexmanager.ui.components.PolygonShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

data class SplitInstallState(
    val loading: Boolean = true,
    val error: String? = null,
    val bundle: SplitBundle? = null,
    val details: ApkDetails? = null,
    val selected: Set<String> = emptySet(),
    val progress: Float? = null,
    val waiting: Boolean = false,
    val result: String? = null,
    val success: Boolean = false,
    /** Прогресс объединения частей в один APK. */
    val merging: Float? = null,
)

class SplitInstallViewModel(app: Application, private val path: String, private val iconPx: Int) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(SplitInstallState())
    val state: StateFlow<SplitInstallState> = _state.asStateFlow()
    private val work = File(app.cacheDir, "split/${System.nanoTime()}")
    private var job: Job? = null

    init {
        SplitInstaller.consume()
        viewModelScope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) {
                    val dm = getApplication<Application>().resources.displayMetrics
                    SplitInstaller.read(File(path), SplitInstaller.deviceAbis(), dm.densityDpi, SplitInstaller.deviceLanguages())
                }
            }
            r.onSuccess { b ->
                _state.update { it.copy(loading = false, bundle = b, selected = b.splits.filter { s -> s.recommended }.map { s -> s.entry }.toSet()) }
                loadDetails(b)
            }.onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: e.javaClass.simpleName) } }
        }
    }

    /** Подпись и иконка — из основного APK, распакованного во временную папку. */
    private fun loadDetails(b: SplitBundle) {
        val base = b.base ?: return
        viewModelScope.launch {
            val d = runCatching {
                val file = withContext(Dispatchers.IO) {
                    work.mkdirs()
                    File(work, "base.apk").also { out ->
                        ZipFile(path).use { z -> z.getInputStream(z.getEntry(base.entry)).use { i -> out.outputStream().use { i.copyTo(it) } } }
                    }
                }
                ApkAnalyzer.analyze(getApplication(), file.path, iconPx)
            }.getOrNull()
            _state.update { it.copy(details = d) }
        }
    }

    fun toggle(s: SplitApk) {
        if (s.group == SplitGroup.Base || state.value.progress != null) return
        _state.update { st -> st.copy(selected = if (s.entry in st.selected) st.selected - s.entry else st.selected + s.entry) }
    }

    fun install() {
        val b = state.value.bundle ?: return
        if (job?.isActive == true) return
        val entries = b.splits.filter { it.entry in state.value.selected }.map { it.entry }
        _state.update { it.copy(progress = 0f, result = null, success = false) }
        job = viewModelScope.launch {
            val r = runCatching {
                SplitInstaller.install(getApplication(), File(path), entries, b) { p -> _state.update { it.copy(progress = p) } }
            }
            r.onSuccess { id ->
                _state.update { it.copy(progress = null, waiting = true) }
                val outcome = SplitInstaller.results.filterNotNull().first { it.sessionId == id }
                SplitInstaller.consume()
                val obb = if (outcome.ok && b.obb.isNotEmpty()) SplitInstaller.copyObb(File(path), b) else 0
                val obbNote = when {
                    b.obb.isEmpty() -> ""
                    obb == b.obb.size -> " · OBB скопированы ($obb)"
                    else -> " · OBB скопировано $obb из ${b.obb.size} — остальные распакуйте в Android/obb вручную"
                }
                _state.update { it.copy(waiting = false, result = outcome.message + if (outcome.ok) obbNote else "", success = outcome.ok) }
            }.onFailure { e ->
                _state.update { it.copy(progress = null, waiting = false, result = "Не удалось начать установку: ${e.message ?: e.javaClass.simpleName}", success = false) }
            }
        }
    }

    /** Один APK из основного и выбранных частей (рядом с пакетом), затем подпись. */
    fun mergeToApk() {
        val b = state.value.bundle ?: return
        val base = b.base ?: return
        if (state.value.merging != null || job?.isActive == true) return
        val src = File(path)
        val chosen = b.splits.filter { it.entry in state.value.selected && it.group != SplitGroup.Base }.map { it.entry }
        _state.update { it.copy(merging = 0f, result = null) }
        job = viewModelScope.launch {
            val r = runCatching {
                // Рядом с пакетом, а если туда писать нельзя — в «Загрузки/WexManager».
                @Suppress("DEPRECATION")
                val dir = src.parentFile?.takeIf { it.canWrite() }
                    ?: File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "WexManager").apply { mkdirs() }
                val stem = src.nameWithoutExtension + "_merged"
                var out = File(dir, "$stem.apk")
                var n = 2
                while (out.exists()) out = File(dir, "$stem ($n).apk").also { n++ }
                SplitMerger.merge(src, base.entry, chosen, out, File(work, "merge")) { p -> _state.update { it.copy(merging = p) } }
                out
            }
            r.onSuccess { out ->
                val c = getApplication<Application>().appContainer
                c.taskManager.signApk(out, c.userData.data.value.activeSigningKey)
                _state.update { it.copy(merging = null, result = "Собран ${out.name} (${chosen.size + 1} част.) — подпись: ${out.nameWithoutExtension}_signed.apk", success = false) }
            }.onFailure { e ->
                _state.update { it.copy(merging = null, result = "Не удалось объединить: ${e.message ?: e.javaClass.simpleName}") }
            }
        }
    }

    fun cancelWaiting() {
        job?.cancel()
        _state.update { it.copy(progress = null, waiting = false) }
    }

    override fun onCleared() {
        work.deleteRecursively()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SplitInstallScreen(path: String, onBack: () -> Unit, onOpenApp: (String) -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val iconPx = with(LocalDensity.current) { 72.dp.roundToPx() }
    val vm: SplitInstallViewModel = viewModel(key = "split:$path") { SplitInstallViewModel(app, path, iconPx) }
    val state by vm.state.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.result) { state.result?.let { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
                title = {
                    Column {
                        Text("Установка пакета")
                        Text(File(path).name, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                    }
                },
                actions = {
                    IconButton(
                        onClick = vm::mergeToApk,
                        enabled = state.bundle?.base != null && state.merging == null && state.progress == null && !state.waiting,
                        shapes = IconButtonDefaults.shapes(),
                    ) { Icon(Icons.Rounded.CallMerge, "Объединить в один APK") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.surface),
            )
        },
        bottomBar = {
            val b = state.bundle
            if (b != null) Surface(color = scheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val p = state.progress
                    val m = state.merging
                    when {
                        m != null -> LinearWavyProgressIndicator(progress = { m }, modifier = Modifier.fillMaxWidth())
                        p != null -> LinearWavyProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                        state.waiting -> LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                    }
                    val chosen = b.splits.filter { it.entry in state.selected }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when {
                                m != null -> "Объединение в APK… ${(m * 100).toInt()}%"
                                p != null -> "Передача частей… ${(p * 100).toInt()}%"
                                state.waiting -> "Ждём подтверждения установки"
                                else -> "${chosen.size} из ${b.splits.size} · ${Format.size(chosen.sumOf { it.size })}"
                            },
                            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                        )
                        if (state.waiting) TextButton(onClick = vm::cancelWaiting) { Text("Скрыть") }
                        if (state.success && state.details != null) {
                            TextButton(onClick = { onOpenApp(state.details!!.packageName) }) { Text("Открыть") }
                        }
                        FilledTonalButton(
                            onClick = vm::install,
                            enabled = p == null && m == null && !state.waiting && b.base != null,
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Icon(Icons.Rounded.InstallMobile, null, Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(if (state.success) "Ещё раз" else "Установить")
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = scheme.surface,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val b = state.bundle
            when {
                state.loading -> LoadingIndicator(Modifier.size(64.dp).align(Alignment.Center))
                state.error != null || b == null -> ShapeMessage(Icons.Rounded.Error, "Не удалось прочитать пакет", state.error, error = true)
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    item(key = "header") { Header(state.details, b) }
                    SplitGroup.entries.forEach { g ->
                        val items = b.splits.filter { it.group == g }
                        if (items.isEmpty()) return@forEach
                        item(key = "h:$g") {
                            Text(g.title, style = MaterialTheme.typography.titleSmall, color = scheme.primary, modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 6.dp))
                        }
                        itemsIndexed(items, key = { _, s -> s.entry }) { i, s ->
                            SegmentedListItem(
                                onClick = { vm.toggle(s) },
                                enabled = s.group != SplitGroup.Base,
                                shapes = ListItemDefaults.segmentedShapes(i, items.size),
                                supportingContent = {
                                    Text(
                                        listOfNotNull(Format.size(s.size), "рекомендуется".takeIf { s.recommended && s.group != SplitGroup.Base }).joinToString(" · "),
                                        color = if (s.recommended) scheme.primary else scheme.onSurfaceVariant,
                                    )
                                },
                                trailingContent = { Checkbox(checked = s.entry in state.selected, onCheckedChange = null, enabled = s.group != SplitGroup.Base) },
                            ) { Text(label(s), fontFamily = if (s.group == SplitGroup.Base) null else FontFamily.Monospace) }
                        }
                    }
                    if (b.obb.isNotEmpty()) item(key = "obb") {
                        Text(
                            "OBB-файлов: ${b.obb.size}. Будут скопированы в Android/obb/${b.packageName ?: "…"}",
                            Modifier.padding(start = 8.dp, top = 16.dp), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

private fun copyPackageName(context: Context, packageName: String) {
    val copied = runCatching {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: error("clipboard")
        cm.setPrimaryClip(ClipData.newPlainText("package", packageName))
    }.isSuccess
    if (copied) Toast.makeText(context, "Пакет скопирован", Toast.LENGTH_SHORT).show()
}

private fun label(s: SplitApk): String = when (s.group) {
    SplitGroup.Base -> s.name + ".apk"
    SplitGroup.Language -> runCatching {
        val l = java.util.Locale.forLanguageTag(s.qualifier.replace('_', '-'))
        "${s.qualifier} · ${l.getDisplayName(java.util.Locale.getDefault()).replaceFirstChar { it.uppercase() }}"
    }.getOrDefault(s.qualifier)
    else -> s.qualifier
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Header(d: ApkDetails?, b: SplitBundle) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val shape = remember { PolygonShape(MaterialShapes.Cookie9Sided) }
    val packageName = d?.packageName?.takeIf { it.isNotBlank() } ?: b.packageName?.takeIf { it.isNotBlank() }
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        val image = remember(d?.icon) { d?.icon?.bitmap?.asImageBitmap() }
        if (d == null) ShapedIcon(Icons.Rounded.Android, MaterialShapes.Cookie9Sided, scheme.primaryContainer, scheme.onPrimaryContainer, size = 72.dp)
        else AppIconImage(image, adaptive = d.icon?.adaptive == true, size = 72.dp, shape = shape)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(d?.label ?: packageName ?: "Пакет приложения", style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (packageName != null) {
                Text(packageName, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                TextButton(
                    onClick = { copyPackageName(context, packageName) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Копировать")
                }
            }
            if (d != null) {
                Text("Версия ${d.versionName ?: "?"} (${d.versionCode}) · Android ${d.minSdk}+", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                d.installed?.let { inst ->
                    val cmp = d.versionComparison ?: 0
                    Text(
                        "Установлена ${inst.versionName ?: "?"} (${inst.versionCode})" + when { cmp > 0 -> " · обновление"; cmp < 0 -> " · откат на старую"; else -> " · та же версия" },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (cmp < 0) scheme.error else scheme.primary,
                    )
                }
            }
        }
    }
}
