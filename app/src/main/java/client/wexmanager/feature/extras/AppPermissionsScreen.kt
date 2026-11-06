package client.wexmanager.feature.extras

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class AppPermission(
    val name: String,
    val label: String?,
    val granted: Boolean,
    val level: String,
)

/** Запрошенные разрешения этого приложения и отметка, какие из них выданы. */
@Composable
fun AppPermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val pm = remember(context) { context.packageManager }
    val scheme = MaterialTheme.colorScheme
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<AppPermission>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var token by remember { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(items, query) {
        val q = query.trim()
        if (q.isEmpty()) items else items.filter { it.matches(q) }
    }

    LaunchedEffect(token) {
        val mine = token
        if (items.isEmpty()) loading = true
        error = null
        try {
            val loaded = withContext(Dispatchers.IO) { loadAppPermissions(pm, context.packageName) }
            if (mine != token) return@LaunchedEffect
            items = loaded
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (mine != token) return@LaunchedEffect
            items = emptyList()
            error = e.message?.takeIf { it.isNotBlank() } ?: "Не удалось прочитать разрешения"
        } finally {
            if (mine == token) loading = false
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Разрешения приложения") },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
                    }
                },
                actions = {
                    if (loading && items.isNotEmpty()) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            LoadingIndicator(Modifier.size(24.dp))
                        }
                    } else {
                        IconButton(
                            onClick = { token++ },
                            enabled = !loading,
                            shapes = IconButtonDefaults.shapes(),
                        ) { Icon(Icons.Rounded.Refresh, "Обновить") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.surface,
                    scrolledContainerColor = scheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                label = { Text("Фильтр") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.Close, "Очистить фильтр")
                        }
                    }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search,
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                ),
            )
            Box(Modifier.fillMaxSize()) {
                when {
                    loading && items.isEmpty() -> CenterMessage(
                        icon = null,
                        title = "Чтение разрешений…",
                        loading = true,
                    )
                    error != null -> CenterMessage(
                        icon = Icons.Rounded.Error,
                        title = "Не удалось прочитать разрешения",
                        subtitle = error,
                    ) {
                        FilledTonalButton(
                            onClick = { token++ },
                            shapes = ButtonDefaults.shapes(),
                        ) { Text("Повторить") }
                    }
                    filtered.isEmpty() && query.isNotBlank() -> CenterMessage(
                        icon = Icons.Rounded.SearchOff,
                        title = "Ничего не найдено",
                        subtitle = "По запросу «${query.trim()}» разрешений нет",
                    )
                    filtered.isEmpty() -> CenterMessage(
                        icon = Icons.Rounded.Shield,
                        title = "Разрешения не запрошены",
                        subtitle = "PackageManager не вернул uses-permission",
                    )
                    else -> PermissionList(filtered, items.count { it.granted }, items.size, query.isNotBlank()) { item ->
                        scope.launch {
                            val copied = copyPermission(context, item.name)
                            snackbar.showSnackbar(if (copied) "Скопировано" else "Не удалось скопировать")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionList(
    items: List<AppPermission>,
    grantedCount: Int,
    total: Int,
    filtered: Boolean,
    onCopy: (AppPermission) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(key = "summary", contentType = "summary") {
            Text(
                if (filtered) "Найдено ${items.size}" else "Выдано $grantedCount из $total",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        itemsIndexed(items, key = { _, item -> item.name }, contentType = { _, _ -> "perm" }) { index, item ->
            val short = item.name.removePrefix("android.permission.")
            val headline = item.label?.takeIf { it.isNotBlank() && it != item.name } ?: short
            val mark = if (item.granted) "Выдано" else "Нет"
            SegmentedListItem(
                onClick = { onCopy(item) },
                shapes = ListItemDefaults.segmentedShapes(index, items.size),
                leadingContent = {
                    Icon(
                        if (item.granted) Icons.Rounded.CheckCircle else Icons.Rounded.Shield,
                        mark,
                        tint = if (item.granted) scheme.primary else scheme.outline,
                    )
                },
                supportingContent = {
                    Text(
                        listOf(item.name, item.level).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 2,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                },
                trailingContent = {
                    Text(
                        mark,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (item.granted) scheme.primary else scheme.error,
                    )
                },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = if (index == 0) 4.dp else ListItemDefaults.SegmentedGap),
            ) {
                Text(headline, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CenterMessage(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    loading: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            LoadingIndicator(Modifier.size(64.dp))
        } else if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

private fun AppPermission.matches(query: String): Boolean {
    if (query.equals("выдано", true)) return granted
    if (query.equals("не выдано", true) || query.equals("нет", true)) return !granted
    return name.contains(query, true) ||
        label?.contains(query, true) == true ||
        level.contains(query, true)
}

private fun loadAppPermissions(pm: PackageManager, packageName: String): List<AppPermission> {
    val info = packageInfo(pm, packageName)
    val names = info.requestedPermissions ?: return emptyList()
    val flags = info.requestedPermissionsFlags
    val seen = HashSet<String>()
    val result = ArrayList<AppPermission>(names.size)
    for (index in names.indices) {
        val name = names[index]
        if (name.isNullOrBlank() || !seen.add(name)) continue
        val flag = flags?.getOrNull(index)
        val granted = isGranted(pm, packageName, name, flag)
        val declared = permissionInfo(pm, name)
        val label = declared?.let { runCatching { it.loadLabel(pm).toString() }.getOrNull() }
            ?.takeIf { it.isNotBlank() && it != name }
        result += AppPermission(name, label, granted, levelOf(declared))
    }
    result.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    return result
}

/** Флаг из GET_PERMISSIONS, иначе живое состояние checkPermission. */
private fun isGranted(pm: PackageManager, packageName: String, name: String, flag: Int?): Boolean {
    if (flag != null && (flag and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) return true
    return pm.checkPermission(name, packageName) == PackageManager.PERMISSION_GRANTED
}

@Suppress("DEPRECATION")
private fun packageInfo(pm: PackageManager, packageName: String): PackageInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
    } else {
        pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
    }

@Suppress("DEPRECATION")
private fun permissionInfo(pm: PackageManager, name: String): PermissionInfo? =
    runCatching { pm.getPermissionInfo(name, 0) }.getOrNull()

private fun levelOf(info: PermissionInfo?): String {
    if (info == null) return "неизвестно"
    val base = when (info.protection) {
        PermissionInfo.PROTECTION_DANGEROUS -> "опасное"
        PermissionInfo.PROTECTION_NORMAL -> "обычное"
        PermissionInfo.PROTECTION_SIGNATURE -> "подпись"
        PermissionInfo.PROTECTION_INTERNAL -> "внутреннее"
        else -> "другое"
    }
    val flags = info.protectionFlags
    val suffix = when {
        (flags and PermissionInfo.PROTECTION_FLAG_PRIVILEGED) != 0 -> "привилегированное"
        (flags and PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) != 0 -> "разработка"
        else -> null
    }
    return if (suffix == null) base else "$base · $suffix"
}

private fun copyPermission(context: Context, name: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: error("clipboard")
    manager.setPrimaryClip(ClipData.newPlainText("permission", name))
    true
} catch (_: Exception) {
    false
}
