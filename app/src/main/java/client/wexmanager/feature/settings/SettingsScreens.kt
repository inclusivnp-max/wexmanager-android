package client.wexmanager.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardTab
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.WrapText
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Interests
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import client.wexmanager.appContainer
import client.wexmanager.core.fs.FileItem
import client.wexmanager.core.settings.AnimationSpeed
import client.wexmanager.core.settings.AppSettings
import client.wexmanager.core.settings.DateFormatOption
import client.wexmanager.core.settings.DualPaneMode
import client.wexmanager.core.settings.EditorFont
import client.wexmanager.core.settings.EditorThemeOption
import client.wexmanager.core.settings.ListDensity
import client.wexmanager.core.settings.PaletteStyleOption
import client.wexmanager.core.settings.SizeUnits
import client.wexmanager.core.settings.ThemeMode
import client.wexmanager.core.settings.ViewMode
import client.wexmanager.feature.browser.FileRow
import client.wexmanager.ui.components.ConnectedChoice
import client.wexmanager.ui.components.FileIcon
import client.wexmanager.ui.components.MorphShape
import client.wexmanager.ui.navigation.Route
import client.wexmanager.ui.theme.LocalAppSettings
import client.wexmanager.ui.theme.LocalIsDarkTheme
import client.wexmanager.ui.theme.dynamicColorSupported
import client.wexmanager.ui.theme.toKolor
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.dynamicColorScheme
import androidx.graphics.shapes.Morph

@Composable
private fun rememberSettingsUpdater(): ((AppSettings) -> AppSettings) -> Unit {
    val context = LocalContext.current
    return remember { { t -> context.appContainer.settings.update(t) } }
}

// ------------------------------------------------------------------------ Главная

/** Индекс для поиска по настройкам: название → раздел. */
private val SETTINGS_INDEX: List<Triple<String, String, Route>> = listOf(
    Triple("Тема: авто, светлая, тёмная", "внешний вид", Route.SettingsAppearance),
    Triple("Чёрный фон (AMOLED)", "внешний вид", Route.SettingsAppearance),
    Triple("Цвета обоев (Material You)", "внешний вид", Route.SettingsAppearance),
    Triple("Основной цвет (акцент)", "внешний вид", Route.SettingsAppearance),
    Triple("Стиль палитры", "внешний вид", Route.SettingsAppearance),
    Triple("Контраст", "внешний вид", Route.SettingsAppearance),
    Triple("Скорость анимаций", "внешний вид", Route.SettingsAppearance),
    Triple("Стиль иконок: тон, заливка, контур, моно", "иконки", Route.SettingsIcons),
    Triple("Форма иконок", "иконки", Route.SettingsIcons),
    Triple("Цветные папки", "иконки", Route.SettingsIcons),
    Triple("Иконпаки: импорт, загрузка, шаблон", "иконки", Route.SettingsIcons),
    Triple("Своя иконка для расширения", "иконки", Route.SettingsIcons),
    Triple("Список или сетка", "список файлов", Route.SettingsFileList),
    Triple("Плотность списка", "список файлов", Route.SettingsFileList),
    Triple("Размер шрифта имён", "список файлов", Route.SettingsFileList),
    Triple("Миниатюры", "список файлов", Route.SettingsFileList),
    Triple("Расширения файлов", "список файлов", Route.SettingsFileList),
    Triple("Количество элементов в папках", "список файлов", Route.SettingsFileList),
    Triple("Права доступа rwx", "список файлов", Route.SettingsFileList),
    Triple("Формат даты", "список файлов", Route.SettingsFileList),
    Triple("Единицы размера", "список файлов", Route.SettingsFileList),
    Triple("Скрытые файлы", "список файлов", Route.SettingsFileList),
    Triple("Папки сначала", "список файлов", Route.SettingsFileList),
    Triple("Полоса быстрой прокрутки", "список файлов", Route.SettingsFileList),
    Triple("Режим панелей: две, одна, рядом", "поведение", Route.SettingsBehavior),
    Triple("Запоминать открытые папки", "поведение", Route.SettingsBehavior),
    Triple("Автообновление списка", "поведение", Route.SettingsBehavior),
    Triple("Выделение касанием иконки", "поведение", Route.SettingsBehavior),
    Triple("Вибрация", "поведение", Route.SettingsBehavior),
    Triple("Двойное «назад» для выхода", "поведение", Route.SettingsBehavior),
    Triple("Подтверждать удаление", "поведение", Route.SettingsBehavior),
    Triple("Корзина", "поведение", Route.SettingsBehavior),
    Triple("Открывать архивы как папки", "поведение", Route.SettingsBehavior),
    Triple("Частые пароли архивов", "поведение", Route.SettingsBehavior),
    Triple("Root-доступ (su)", "поведение", Route.SettingsBehavior),
    Triple("Открытие по расширению («всегда открывать так»)", "поведение", Route.SettingsBehavior),
    Triple("Тема подсветки кода", "редактор", Route.SettingsEditor),
    Triple("Шрифт редактора", "редактор", Route.SettingsEditor),
    Triple("Размер шрифта редактора", "редактор", Route.SettingsEditor),
    Triple("Номера строк", "редактор", Route.SettingsEditor),
    Triple("Перенос строк", "редактор", Route.SettingsEditor),
    Triple("Табуляция и отступы", "редактор", Route.SettingsEditor),
    Triple("Панель символов", "редактор", Route.SettingsEditor),
    Triple("Автосохранение", "редактор", Route.SettingsEditor),
    Triple("Кодировка по умолчанию", "редактор", Route.SettingsEditor),
    Triple("Ключи подписи APK: импорт .p12, .bks, .pk8", "подпись", Route.SettingsSigning),
    Triple("Экспорт и импорт настроек", "резервная копия", Route.SettingsBackup),
    Triple("Сброс настроек", "резервная копия", Route.SettingsBackup),
)

@Composable
fun SettingsHomeScreen(onBack: () -> Unit, onNavigate: (Route) -> Unit) {
    var query by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    val results = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) emptyList() else SETTINGS_INDEX.filter { (t, sec, _) -> t.contains(q, true) || sec.contains(q, true) }
    }
    SettingsScaffold("Настройки", onBack) {
        item(key = "search") {
            androidx.compose.material3.OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Поиск настройки") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
            )
        }
        if (query.isNotBlank()) {
            if (results.isEmpty()) {
                item(key = "nores") { SettingsHint("Ничего не найдено") }
            } else {
                settingsGroup("Найдено: ${results.size}", key = "results") {
                    results.forEach { (title, section, route) ->
                        click(title, null, section.replaceFirstChar { it.uppercase() }) { onNavigate(route) }
                    }
                }
            }
            return@SettingsScaffold
        }
        item(key = "hero") { SettingsHero() }
        settingsGroup("Оформление") {
            click("Внешний вид", Icons.Rounded.Palette, "Тема, цвета, палитры, анимации") { onNavigate(Route.SettingsAppearance) }
            click("Иконки файлов", Icons.Rounded.Interests, "Стиль, формы, иконпаки, свои иконки") { onNavigate(Route.SettingsIcons) }
            click("Список файлов", Icons.Rounded.ViewList, "Вид, плотность, сортировка, детали") { onNavigate(Route.SettingsFileList) }
        }
        settingsGroup("Работа") {
            click("Поведение", Icons.Rounded.TouchApp, "Панели, удаление, жесты, архивы") { onNavigate(Route.SettingsBehavior) }
            click("Редактор", Icons.Rounded.Code, "Тема кода, шрифт, отступы, автосохранение") { onNavigate(Route.SettingsEditor) }
            click("Ключи подписи APK", Icons.Rounded.Key, "Встроенный ключ или свой: PKCS12, BKS, pk8") { onNavigate(Route.SettingsSigning) }
        }
        settingsGroup("Данные") {
            click("Резервная копия настроек", Icons.Rounded.SettingsBackupRestore, "Экспорт и импорт в JSON") { onNavigate(Route.SettingsBackup) }
            click("О приложении", Icons.Rounded.Info, "Версия, возможности, лицензии") { onNavigate(Route.About) }
        }
    }
}

@Composable
private fun SettingsHero() {
    val scheme = MaterialTheme.colorScheme
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "hero")
    val p by transition.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(3000), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "p",
    )
    val morph = remember { Morph(MaterialShapes.SoftBurst, MaterialShapes.Cookie9Sided) }
    Surface(
        color = scheme.primaryContainer,
        shape = RoundedCornerShape(32.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp).clip(MorphShape(morph, p)).background(scheme.onPrimaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = scheme.primaryContainer, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(18.dp))
            Column {
                Text("Сделайте его своим", style = MaterialTheme.typography.titleLarge, color = scheme.onPrimaryContainer)
                Text(
                    "Все изменения применяются мгновенно",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

// --------------------------------------------------------------- Внешний вид

private val SEEDS = listOf(
    0xFF4F6BED, 0xFF6750A4, 0xFF006A6A, 0xFF2E7D32, 0xFF8BC34A, 0xFFE2A93B, 0xFFE65100, 0xFFD32F2F,
    0xFFC2185B, 0xFF8E24AA, 0xFF5D4037, 0xFF455A64, 0xFF00897B, 0xFF0288D1, 0xFF303F9F, 0xFF212121,
).map { it.toInt() }

private fun PaletteStyleOption.title(): String = when (this) {
    PaletteStyleOption.TonalSpot -> "Тональная"
    PaletteStyleOption.Vibrant -> "Яркая"
    PaletteStyleOption.Expressive -> "Экспрессивная"
    PaletteStyleOption.Fidelity -> "Точная"
    PaletteStyleOption.Content -> "Контентная"
    PaletteStyleOption.Rainbow -> "Радуга"
    PaletteStyleOption.FruitSalad -> "Фруктовая"
    PaletteStyleOption.Neutral -> "Нейтральная"
    PaletteStyleOption.Monochrome -> "Монохром"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceSettingsScreen(onBack: () -> Unit) {
    val s = LocalAppSettings.current
    val update = rememberSettingsUpdater()
    val dark = LocalIsDarkTheme.current
    SettingsScaffold("Внешний вид", onBack) {
        item(key = "preview") { ThemePreview() }
        settingsGroup("Тема") {
            custom { shapes ->
                SegmentedListItem(onClick = {}, shapes = shapes, supportingContent = {
                    ConnectedChoice(
                        options = ThemeMode.entries,
                        selected = s.themeMode,
                        onSelect = { v -> update { it.copy(themeMode = v) } },
                        label = { when (it) { ThemeMode.System -> "Авто"; ThemeMode.Light -> "Светлая"; ThemeMode.Dark -> "Тёмная" } },
                        icon = { when (it) { ThemeMode.System -> Icons.Rounded.BrightnessMedium; ThemeMode.Light -> Icons.Rounded.LightMode; ThemeMode.Dark -> Icons.Rounded.DarkMode } },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }) { Text("Режим") }
            }
            switch("Чёрный фон (AMOLED)", s.amoled, Icons.Rounded.Brightness4, "Чистый чёрный в тёмной теме — экономит заряд") { v -> update { it.copy(amoled = v) } }
            if (dynamicColorSupported) {
                switch("Цвета обоев (Material You)", s.dynamicColor, Icons.Rounded.Wallpaper, "Палитра из системных обоев") { v -> update { it.copy(dynamicColor = v) } }
            }
        }
        if (!s.dynamicColor || !dynamicColorSupported) {
            settingsGroup("Основной цвет") {
                custom { shapes ->
                    SegmentedListItem(onClick = {}, shapes = shapes, supportingContent = {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(vertical = 8.dp),
                        ) {
                            SEEDS.forEach { seed -> SeedSwatch(Color(seed), selected = s.seedColor == seed) { update { it.copy(seedColor = seed) } } }
                        }
                    }) { Text("Акцент") }
                }
            }
        }
        settingsGroup("Палитра") {
            custom { shapes ->
                SegmentedListItem(onClick = {}, shapes = shapes, supportingContent = {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(PaletteStyleOption.entries) { style ->
                            PaletteCard(style, Color(s.seedColor).takeUnless { s.dynamicColor && dynamicColorSupported } ?: MaterialTheme.colorScheme.primary, dark, selected = s.paletteStyle == style) {
                                update { it.copy(paletteStyle = style) }
                            }
                        }
                    }
                }) { Text("Стиль палитры") }
            }
            slider("Контраст", s.contrast, -0.5f..1f, steps = 5, Icons.Rounded.Contrast, valueLabel = {
                when {
                    it < -0.2f -> "низкий"
                    it < 0.2f -> "обычный"
                    it < 0.7f -> "средний"
                    else -> "высокий"
                }
            }) { v -> update { it.copy(contrast = v) } }
        }
        settingsGroup("Движение") {
            choice(
                "Скорость анимаций",
                AnimationSpeed.entries,
                s.animationSpeed,
                label = { when (it) { AnimationSpeed.Off -> "Выключены"; AnimationSpeed.Fast -> "Быстрые"; AnimationSpeed.Normal -> "Обычные (Expressive)"; AnimationSpeed.Relaxed -> "Плавные" } },
                icon = Icons.Rounded.Animation,
            ) { v -> update { it.copy(animationSpeed = v) } }
        }
    }
}

@Composable
private fun SeedSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    val progress by animateFloatAsState(if (selected) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "seed")
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    Box(
        Modifier
            .size(44.dp)
            .clip(MorphShape(morph, progress))
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White)
    }
}

@Composable
private fun PaletteCard(style: PaletteStyleOption, seed: Color, dark: Boolean, selected: Boolean, onClick: () -> Unit) {
    val scheme = remember(style, seed, dark) {
        dynamicColorScheme(seedColor = seed, isDark = dark, isAmoled = false, style = style.toKolor(), specVersion = ColorSpec.SpecVersion.SPEC_2025)
    }
    val border by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, label = "pb")
    Column(
        Modifier
            .width(92.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(2.dp, border, RoundedCornerShape(20.dp))
            .background(scheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape)) {
            Column {
                Box(Modifier.fillMaxWidth().height(26.dp).background(scheme.primary))
                Row {
                    Box(Modifier.width(26.dp).height(26.dp).background(scheme.secondaryContainer))
                    Box(Modifier.width(26.dp).height(26.dp).background(scheme.tertiary))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(style.title(), style = MaterialTheme.typography.labelMedium, color = scheme.onSurface, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/** Мини-превью интерфейса в текущей теме. */
@Composable
private fun ThemePreview() {
    val scheme = MaterialTheme.colorScheme
    val now = remember { System.currentTimeMillis() }
    val samples = remember {
        listOf(
            FileItem("/p/Documents", "Documents", true, 0, now - 3_600_000, childCount = 12),
            FileItem("/p/app-release.apk", "app-release.apk", false, 18_400_000, now - 86_400_000),
            FileItem("/p/Main.kt", "Main.kt", false, 2_480, now - 600_000),
        )
    }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = scheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.primaryContainer, scheme.secondaryContainer, scheme.tertiaryContainer).forEach {
                    Box(Modifier.padding(end = 6.dp).size(22.dp).clip(CircleShape).background(it))
                }
            }
            Spacer(Modifier.height(8.dp))
            samples.forEachIndexed { i, item ->
                FileRow(item = item, selected = i == 1, highlight = false, onClick = {}, onLongClick = {}, onIconClick = {})
            }
        }
    }
}

// ------------------------------------------------------------- Список файлов

@Composable
fun FileListSettingsScreen(onBack: () -> Unit) {
    val s = LocalAppSettings.current
    val update = rememberSettingsUpdater()
    SettingsScaffold("Список файлов", onBack) {
        item(key = "preview") { ThemePreview() }
        settingsGroup("Вид") {
            choice("Режим", ViewMode.entries, s.viewMode, { if (it == ViewMode.List) "Список" else "Сетка" }, Icons.Rounded.GridView) { v -> update { it.copy(viewMode = v) } }
            choice("Плотность", ListDensity.entries, s.listDensity, {
                when (it) { ListDensity.Compact -> "Плотно"; ListDensity.Comfortable -> "Обычно"; ListDensity.Spacious -> "Просторно" }
            }, Icons.Rounded.UnfoldMore) { v -> update { it.copy(listDensity = v) } }
            if (s.viewMode == ViewMode.Grid) {
                slider("Размер ячейки сетки", s.gridMinCellDp.toFloat(), 80f..180f, steps = 9, Icons.Rounded.Straighten, { "${it.toInt()} dp" }) { v -> update { it.copy(gridMinCellDp = v.toInt()) } }
            }
            slider("Размер шрифта имён", s.fileListFontScale, 0.85f..1.3f, steps = 8, Icons.Rounded.FormatSize, { "${(it * 100).toInt()}%" }) { v -> update { it.copy(fileListFontScale = v) } }
            choice("Строк в имени", listOf(1, 2, 3), s.fileNameMaxLines, { "$it" }, Icons.AutoMirrored.Rounded.WrapText) { v -> update { it.copy(fileNameMaxLines = v) } }
        }
        settingsGroup("Сведения о файлах") {
            switch("Миниатюры", s.showThumbnails, Icons.Rounded.Image, "Изображения, видео, обложки, иконки APK") { v -> update { it.copy(showThumbnails = v) } }
            switch("Расширения файлов", s.showExtensions, Icons.Rounded.Description) { v -> update { it.copy(showExtensions = v) } }
            switch("Количество элементов в папках", s.showFolderItemCount, Icons.Rounded.Numbers) { v -> update { it.copy(showFolderItemCount = v) } }
            switch("Права доступа (rwx)", s.showPermissions, Icons.Rounded.Lock) { v -> update { it.copy(showPermissions = v) } }
            choice("Формат даты", DateFormatOption.entries, s.dateFormat, {
                when (it) { DateFormatOption.Relative -> "Относительный (вчера, 5 мин назад)"; DateFormatOption.Short -> "Краткий (03.10.26 12:30)"; DateFormatOption.Full -> "Полный" }
            }, Icons.Rounded.CalendarMonth) { v -> update { it.copy(dateFormat = v) } }
            choice("Единицы размера", SizeUnits.entries, s.sizeUnits, { if (it == SizeUnits.Binary) "Двоичные (1 КБ = 1024 Б)" else "Десятичные (1 КБ = 1000 Б)" }, Icons.Rounded.SdStorage) { v -> update { it.copy(sizeUnits = v) } }
            switch("Свободное место в шапке панели", s.showStorageInfo, Icons.Rounded.SdStorage) { v -> update { it.copy(showStorageInfo = v) } }
        }
        settingsGroup("Сортировка") {
            switch("Папки сначала", s.foldersFirst, Icons.Rounded.Folder) { v -> update { it.copy(foldersFirst = v) } }
            switch("Показывать скрытые файлы", s.showHidden, Icons.Rounded.Visibility) { v -> update { it.copy(showHidden = v) } }
            switch("Полоса быстрой прокрутки", s.showScrollbar, Icons.AutoMirrored.Rounded.Sort) { v -> update { it.copy(showScrollbar = v) } }
        }
    }
}

// ---------------------------------------------------------------- Поведение

@Composable
fun BehaviorSettingsScreen(onBack: () -> Unit) {
    val s = LocalAppSettings.current
    val update = rememberSettingsUpdater()
    SettingsScaffold("Поведение", onBack) {
        settingsGroup("Панели") {
            choice("Режим панелей", DualPaneMode.entries, s.dualPaneMode, {
                when (it) { DualPaneMode.Auto -> "Авто: рядом на широком экране"; DualPaneMode.Always -> "Всегда рядом"; DualPaneMode.Single -> "Одна панель" }
            }, Icons.Rounded.ViewColumn) { v -> update { it.copy(dualPaneMode = v) } }
            switch("Запоминать открытые папки", s.rememberLastPaths, Icons.Rounded.FolderOpen) { v -> update { it.copy(rememberLastPaths = v) } }
            switch("Автообновление списка", s.autoRefresh, Icons.Rounded.Refresh, "Следить за изменениями в папке") { v -> update { it.copy(autoRefresh = v) } }
            switch("Подсвечивать папку, из которой вышли", s.highlightReturnedFolder, Icons.Rounded.Highlight) { v -> update { it.copy(highlightReturnedFolder = v) } }
        }
        settingsGroup("Жесты и касания") {
            switch("Выделение касанием иконки", s.tapIconToSelect, Icons.Rounded.TouchApp, "Касание иконки файла выделяет его") { v -> update { it.copy(tapIconToSelect = v) } }
            switch("Вибрация", s.haptics, Icons.Rounded.Vibration) { v -> update { it.copy(haptics = v) } }
            switch("Двойное «назад» для выхода", s.doubleBackToExit, Icons.Rounded.ExitToApp) { v -> update { it.copy(doubleBackToExit = v) } }
        }
        settingsGroup("Файлы") {
            switch("Подтверждать удаление", s.confirmDelete, Icons.Rounded.Delete) { v -> update { it.copy(confirmDelete = v) } }
            switch("Корзина", s.useTrash, Icons.Rounded.DeleteSweep, "Удалённые файлы сначала попадают в корзину") { v -> update { it.copy(useTrash = v) } }
            switch("Открывать архивы как папки", s.openArchivesAsFolders, Icons.Rounded.Compress, "ZIP, 7z, TAR, APK…") { v -> update { it.copy(openArchivesAsFolders = v) } }
            custom { shapes ->
                val context = androidx.compose.ui.platform.LocalContext.current
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                var checking by remember { mutableStateOf(false) }
                SegmentedListItem(
                    onClick = {
                        if (checking) return@SegmentedListItem
                        if (s.rootAccess) { update { it.copy(rootAccess = false) }; return@SegmentedListItem }
                        // Включаем только после реальной проверки: менеджер root покажет запрос разрешения.
                        checking = true
                        scope.launch {
                            val ok = client.wexmanager.core.fs.RootShell.check()
                            checking = false
                            if (ok) update { it.copy(rootAccess = true) }
                            else android.widget.Toast.makeText(context, "Root недоступен или доступ не выдан", android.widget.Toast.LENGTH_LONG).show()
                        }
                    },
                    shapes = shapes,
                    leadingContent = { Icon(Icons.Rounded.AdminPanelSettings, null) },
                    supportingContent = { Text(if (checking) "Проверка доступа…" else "Просмотр /data и системных разделов через su — только чтение") },
                    trailingContent = { androidx.compose.material3.Switch(checked = s.rootAccess, onCheckedChange = null, enabled = !checking) },
                ) { Text("Root-доступ") }
            }
            custom { shapes ->
                val context = androidx.compose.ui.platform.LocalContext.current
                val userData = context.appContainer.userData
                val data by userData.data.collectAsStateWithLifecycle()
                var open by remember { mutableStateOf(false) }
                SegmentedListItem(
                    onClick = { open = true },
                    shapes = shapes,
                    leadingContent = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                    supportingContent = { Text(if (data.openWith.isEmpty()) "Выбор из «Открыть как» для расширений" else "Запомнено: ${data.openWith.size}") },
                ) { Text("Открытие по расширению") }
                if (open) OpenWithDialog(data.openWith, onRemove = { userData.setOpenWith(it, null) }, onDismiss = { open = false })
            }
            custom { shapes ->
                val context = androidx.compose.ui.platform.LocalContext.current
                val userData = context.appContainer.userData
                val passwords by userData.data.collectAsStateWithLifecycle().let { st -> remember(st) { androidx.compose.runtime.derivedStateOf { st.value.archivePasswords } } }
                var open by remember { mutableStateOf(false) }
                SegmentedListItem(
                    onClick = { open = true },
                    shapes = shapes,
                    leadingContent = { Icon(Icons.Rounded.Key, null) },
                    supportingContent = { Text(if (passwords.isEmpty()) "Пока нет — пароли добавляются при распаковке" else "Сохранено: ${passwords.size}") },
                ) { Text("Частые пароли архивов") }
                if (open) ArchivePasswordsDialog(
                    passwords = passwords,
                    onAdd = { userData.addArchivePassword(it) },
                    onRemove = { userData.removeArchivePassword(it) },
                    onDismiss = { open = false },
                )
            }
        }
    }
}

@Composable
private fun OpenWithDialog(map: Map<String, String>, onRemove: (String) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
        title = { Text("Открытие по расширению") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                if (map.isEmpty()) {
                    Text(
                        "Пока ничего не запомнено. В диалоге «Открыть как» отметьте «Всегда открывать так».",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val entries = map.entries.sortedBy { it.key }
                entries.forEachIndexed { i, (ext, mode) ->
                    SegmentedListItem(
                        onClick = {},
                        shapes = ListItemDefaults.segmentedShapes(i, entries.size),
                        supportingContent = { Text(client.wexmanager.core.util.OpenAs.entries.firstOrNull { it.name == mode }?.title ?: mode) },
                        trailingContent = {
                            androidx.compose.material3.IconButton(onClick = { onRemove(ext) }) { Icon(Icons.Rounded.Close, "Сбросить") }
                        },
                    ) { Text(".$ext", fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Готово") } },
    )
}

@Composable
private fun ArchivePasswordsDialog(
    passwords: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var reveal by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Key, null) },
        title = { Text("Частые пароли") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Показываются в окне ввода пароля архива — выберите нужный одним касанием. Хранятся только на этом устройстве и не попадают в резервную копию.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.material3.OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    label = { Text("Новый пароль") },
                    trailingIcon = {
                        androidx.compose.material3.IconButton(
                            onClick = { onAdd(input); input = "" },
                            enabled = input.isNotBlank(),
                        ) { Icon(Icons.Rounded.Add, "Добавить") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (passwords.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Сохранено: ${passwords.size}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        androidx.compose.material3.TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) "Скрыть" else "Показать") }
                    }
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(passwords.distinct(), key = { it }) { pw ->
                            Row(
                                Modifier.fillMaxWidth().animateItem().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (reveal) pw else "•".repeat(pw.length.coerceIn(4, 12)),
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                androidx.compose.material3.IconButton(onClick = { onRemove(pw) }) { Icon(Icons.Rounded.Close, "Удалить") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Готово") } },
    )
}

// ----------------------------------------------------------------- Редактор

private fun EditorThemeOption.title(): String = when (this) {
    EditorThemeOption.MaterialYou -> "Material You (под цвета приложения)"
    EditorThemeOption.Darcula -> "Darcula"
    EditorThemeOption.Monokai -> "Monokai"
    EditorThemeOption.QuietLight -> "Quiet Light"
    EditorThemeOption.SolarizedDark -> "Solarized Dark"
    EditorThemeOption.AyuDark -> "Ayu Dark"
}

@Composable
fun EditorSettingsScreen(onBack: () -> Unit) {
    val s = LocalAppSettings.current
    val update = rememberSettingsUpdater()
    SettingsScaffold("Редактор", onBack) {
        item(key = "preview") { EditorPreview() }
        settingsGroup("Оформление") {
            choice("Тема подсветки", EditorThemeOption.entries, s.editorTheme, { it.title() }, Icons.Rounded.Palette) { v -> update { it.copy(editorTheme = v) } }
            choice("Шрифт", EditorFont.entries, s.editorFont, { if (it == EditorFont.JetBrainsMono) "JetBrains Mono" else "Системный моноширинный" }, Icons.Rounded.FontDownload) { v -> update { it.copy(editorFont = v) } }
            slider("Размер шрифта", s.editorFontSize, 9f..24f, steps = 14, Icons.Rounded.FormatSize, { "${it.toInt()} sp" }) { v -> update { it.copy(editorFontSize = v) } }
            switch("Номера строк", s.editorLineNumbers, Icons.Rounded.FormatListNumbered) { v -> update { it.copy(editorLineNumbers = v) } }
            switch("Закреплять номера строк", s.editorPinLineNumbers, Icons.Rounded.PushPin, "Не прокручиваются по горизонтали") { v -> update { it.copy(editorPinLineNumbers = v) } }
            switch("Подсвечивать текущую строку", s.editorHighlightLine, Icons.Rounded.Highlight) { v -> update { it.copy(editorHighlightLine = v) } }
            switch("Показывать пробелы", s.editorShowWhitespace, Icons.Rounded.SpaceBar) { v -> update { it.copy(editorShowWhitespace = v) } }
            switch("Подсветка парных скобок", s.editorBracketPairs, Icons.Rounded.DataObject) { v -> update { it.copy(editorBracketPairs = v) } }
            switch("Закреплённые заголовки блоков", s.editorStickyScroll, Icons.Rounded.Widgets, "Sticky scroll: функция/класс вверху экрана") { v -> update { it.copy(editorStickyScroll = v) } }
        }
        settingsGroup("Правка") {
            switch("Автоподсказки", s.editorAutoComplete, Icons.Rounded.AutoAwesome, "Слова, функции, теги и шаблоны при наборе; Ctrl+Пробел — вручную") { v -> update { it.copy(editorAutoComplete = v) } }
            switch("Перенос длинных строк", s.editorWordWrap, Icons.AutoMirrored.Rounded.WrapText) { v -> update { it.copy(editorWordWrap = v) } }
            switch("Автоотступ", s.editorAutoIndent, Icons.AutoMirrored.Rounded.KeyboardTab) { v -> update { it.copy(editorAutoIndent = v) } }
            choice("Ширина табуляции", listOf(2, 4, 8), s.editorTabSize, { "$it" }, Icons.AutoMirrored.Rounded.KeyboardTab) { v -> update { it.copy(editorTabSize = v) } }
            switch("Пробелы вместо табуляции", s.editorUseSpaces, Icons.Rounded.SpaceBar) { v -> update { it.copy(editorUseSpaces = v) } }
            switch("Панель символов", s.editorSymbolBar, Icons.Rounded.Keyboard, "Быстрый ввод { } ( ) ; и т.д.") { v -> update { it.copy(editorSymbolBar = v) } }
            switch("Автосохранение", s.editorAutoSave, Icons.Rounded.Save, "При сворачивании приложения") { v -> update { it.copy(editorAutoSave = v) } }
            choice("Кодировка по умолчанию", listOf("UTF-8", "windows-1251", "KOI8-R", "ISO-8859-1", "GBK"), s.editorDefaultEncoding, { it }, Icons.Rounded.Language) { v -> update { it.copy(editorDefaultEncoding = v) } }
        }
    }
}

/** Небольшой статичный фрагмент кода, окрашенный в цвета темы. */
@Composable
private fun EditorPreview() {
    val scheme = MaterialTheme.colorScheme
    val s = LocalAppSettings.current
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = scheme.surface,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
    ) {
        val code = androidx.compose.ui.text.buildAnnotatedString {
            fun span(text: String, color: Color, bold: Boolean = false) {
                pushStyle(androidx.compose.ui.text.SpanStyle(color = color, fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.Bold else null))
                append(text)
                pop()
            }
            span("// Пример подсветки\n", scheme.outline)
            span("fun ", scheme.primary, true); span("greet", scheme.tertiary); span("(name: ", scheme.onSurface); span("String", scheme.secondary); span(") {\n", scheme.onSurface)
            span("    val ", scheme.primary, true); span("count = ", scheme.onSurface); span("42\n", scheme.error)
            span("    println(", scheme.onSurface); span("\"Привет, \$name!\"", scheme.tertiary); span(")\n}", scheme.onSurface)
        }
        Row(Modifier.padding(16.dp)) {
            if (s.editorLineNumbers) {
                Text(
                    "1\n2\n3\n4\n5",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = (s.editorFontSize - 2).coerceAtLeast(9f).let { androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Sp) }),
                    color = scheme.outline,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
            Text(
                code,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = (s.editorFontSize - 2).coerceAtLeast(9f).let { androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Sp) }),
            )
        }
    }
}
