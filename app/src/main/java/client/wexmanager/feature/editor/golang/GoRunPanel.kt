package client.wexmanager.feature.editor.golang

import java.io.File
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun GoToolbarActions(state: GoRunnerState, enabled: Boolean, onRun: () -> Unit, onStop: () -> Unit, onOpen: () -> Unit, compact: Boolean = false) {
    GoTooltip(if (state.running) "Остановить Go" else "Запустить Go") {
        FilledIconButton(onClick = if (state.running) onStop else onRun, enabled = state.running || enabled,
            shapes = IconButtonDefaults.shapes(), colors = if (state.running) IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) else IconButtonDefaults.filledIconButtonColors()) {
            val motion = MaterialTheme.motionScheme
            AnimatedContent(state.running, transitionSpec = {
                (fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = 0.7f))
                    .togetherWith(fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.7f))
            }, label = "go-run-stop") { running ->
                Icon(if (running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, if (running) "Остановить Go" else "Запустить Go")
            }
        }
    }
    if (!compact) GoIconButton(onClick = onOpen, tonal = true, tooltip = "Консоль и параметры запуска") {
        Icon(Icons.Rounded.KeyboardArrowDown, "Панель Go и параметры запуска")
    }
}

@Composable
internal fun GoRunPanel(
    vm: GoRunnerViewModel, state: GoRunnerState, busy: Boolean,
    onDismiss: () -> Unit, onAction: (GoAction, String) -> Unit,
    onJumpError: (GoError) -> Unit = {},
) {
    val context = LocalContext.current
    val project = state.project
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.message(if (granted) "Доступ включён. Нажмите запуск." else "Включите запуск команд Termux в настройках WexManager.")
    }
    var setup by remember { mutableStateOf(false) }
    var configOpen by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(false) }
    var valueAction by remember { mutableStateOf<GoAction?>(null) }
    var value by remember { mutableStateOf("") }
    var follow by remember { mutableStateOf(true) }
    val scroll = rememberScrollState()
    val dragging by scroll.interactionSource.collectIsDraggedAsState()
    val ime = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(dragging) { if (dragging) follow = false }
    LaunchedEffect(state.output, follow, scroll.maxValue) { if (follow) scroll.scrollTo(scroll.maxValue) }
    val ready = project != null && !state.discovering && !state.running && !busy
    val primary = if (project?.file?.name?.endsWith("_test.go") == true) GoAction.TEST else GoAction.RUN
    fun act(action: GoAction) { if (!vm.termuxInstalled() || !vm.hasPermission()) setup = true else onAction(action, "") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (state.action == GoAction.CONSOLE) "Консоль" else "Запуск Go", style = MaterialTheme.typography.titleLarge)
                    Text(if (state.running) File(state.runPath).name else project?.file?.name.orEmpty(), maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis, style = MaterialTheme.typography.bodySmall)
                }
                GoIconButton(onClick = { copyGoText(context, state.output) }, tooltip = "Копировать вывод") { Icon(Icons.Rounded.ContentCopy, "Копировать вывод") }
                GoIconButton(onClick = onDismiss, tooltip = "Свернуть консоль") { Icon(Icons.Rounded.Close, "Свернуть консоль; процесс продолжает работать") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                SplitButtonLayout(modifier = Modifier.weight(1f), leadingButton = {
                    SplitButtonDefaults.LeadingButton(onClick = { if (state.running) vm.stop() else act(primary) },
                        enabled = if (state.running) !state.stopping else ready,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors = if (state.running) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer) else ButtonDefaults.buttonColors()) {
                        Icon(if (state.running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.stopping) "Стоп…" else if (state.running) "Стоп" else if (primary == GoAction.TEST) "Тесты" else "Запуск", modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }, trailingButton = {
                    GoTooltip("Параметры запуска") {
                        SplitButtonDefaults.TrailingButton(onClick = { configOpen = true }, enabled = ready, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(Icons.Rounded.Settings, "Параметры запуска", Modifier.size(20.dp))
                        }
                    }
                })
                GoIconButton(onClick = { act(GoAction.CONSOLE) }, enabled = ready, tonal = true, tooltip = "Консоль проекта") { Icon(Icons.Rounded.Terminal, "Открыть консоль в рабочей папке") }
                Box {
                    GoIconButton(onClick = { more = true }, tonal = true, tooltip = "Действия и зависимости") { Icon(Icons.Rounded.MoreVert, "Действия консоли и зависимости") }
                    DropdownMenuPopup(more, onDismissRequest = { more = false }) {
                        DropdownMenuGroup(shapes = MenuDefaults.groupShape(0,1)) {
                            DropdownMenuItem(text = { Text("Собрать бинарник · go build") }, enabled = ready, onClick = { more = false; onAction(GoAction.BUILD, "") }, shape = MenuDefaults.leadingItemShape)
                            DropdownMenuItem(text = { Text("Проверить · go vet") }, enabled = ready, onClick = { more = false; onAction(GoAction.VET, "") }, shape = MenuDefaults.middleItemShape)
                            DropdownMenuItem(text = { Text("Подтянуть импорты") }, enabled = ready && project?.moduleRoot != null, onClick = { more = false; onAction(GoAction.TIDY, "") }, shape = MenuDefaults.middleItemShape)
                            DropdownMenuItem(text = { Text("Добавить зависимость · go get") }, enabled = ready && project?.moduleRoot != null, onClick = { more = false; value = ""; valueAction = GoAction.GET }, shape = MenuDefaults.middleItemShape)
                            DropdownMenuItem(text = { Text("Создать Go-модуль") }, enabled = ready && project?.moduleRoot == null, onClick = { more = false; value = "example.com/app"; valueAction = GoAction.INIT }, shape = MenuDefaults.middleItemShape)
                            DropdownMenuItem(text = { Text("Тесты проекта") }, enabled = ready, onClick = { more = false; act(GoAction.TEST) }, shape = MenuDefaults.middleItemShape)
                            DropdownMenuItem(text = { Text(if (follow) "Автопрокрутка: включена" else "Автопрокрутка: выключена") }, onClick = { follow = !follow; more = false }, shape = MenuDefaults.middleItemShape)
                            DropdownMenuItem(text = { Text("Закрыть ввод · EOF") }, enabled = state.running && !state.stdinClosed, onClick = { vm.closeInput(); more = false }, shape = MenuDefaults.middleItemShape)
                            DropdownMenuItem(text = { Text("Копировать путь журнала") }, enabled = state.logPath != null, onClick = { state.logPath?.let { copyGoText(context,it) }; more = false }, shape = MenuDefaults.middleItemShape)
                            DropdownMenuItem(text = { Text("Окружение Go / Termux") }, onClick = { setup = true; more = false }, shape = MenuDefaults.trailingItemShape)
                        }
                    }
                }
            }
            state.message?.let { SelectionContainer { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis) } }
            if (state.errors.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                    items(state.errors.take(12)) { error ->
                        AssistChip(
                            onClick = { onJumpError(error) },
                            label = { Text("${error.file.substringAfterLast('/')}:${error.line}:${error.column}", maxLines = 1) },
                            leadingIcon = { Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(16.dp)) },
                        )
                    }
                }
            }
            Text(when {
                state.stopping -> "Останавливаю процессы…"
                state.running -> "Выполняется · ${state.action?.label}"
                state.exitCode != null -> "Завершено · код ${state.exitCode}"
                state.discovering || busy -> "Подготовка…"
                else -> "Готово к запуску"
            }, style = MaterialTheme.typography.labelMedium, color = if (state.exitCode != null && state.exitCode != 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            if (!ime && state.preview.isNotBlank()) SelectionContainer { Text("$ ${state.preview}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            HorizontalDivider()
            SelectionContainer(Modifier.weight(1f).fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
                Text(state.output.ifEmpty { "Вывод программы появится здесь.\nЗначок терминала открывает Bash в папке проекта." },
                    Modifier.fillMaxWidth().verticalScroll(scroll).padding(12.dp), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                val canInput = state.running && !state.stdinClosed && !state.stopping
                fun send() { if (canInput) vm.sendInput(state.inputDraft) }
                if (state.action == GoAction.CONSOLE) Box {
                    GoIconButton(onClick = { history = true }, enabled = state.commandHistory.isNotEmpty()) { Icon(Icons.Rounded.History, "История команд") }
                    DropdownMenuPopup(history, onDismissRequest = { history = false }) {
                        DropdownMenuGroup(shapes = MenuDefaults.groupShape(0,1)) {
                            state.commandHistory.forEach { command -> DropdownMenuItem(text = { Text(command, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { vm.updateInputDraft(command); history = false }, shape = MenuDefaults.middleItemShape) }
                        }
                    }
                }
                GoTextField(state.inputDraft, onValueChange = vm::updateInputDraft, modifier = Modifier.weight(1f), enabled = canInput,
                    shape = MaterialTheme.shapes.extraLarge, placeholder = { Text(if (state.action == GoAction.CONSOLE) "Команда" else "Ввод программы") },
                    trailingIcon = { GoIconButton(onClick = ::send, enabled = canInput, tonal = true, tooltip = "Отправить строку") { Icon(Icons.AutoMirrored.Rounded.Send, "Отправить строку") } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send() }))
            }
        }
    }
    if (setup) AlertDialog(onDismissRequest = { setup = false }, title = { Text("Окружение запуска Go") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Для запуска нужен Termux. Проверка синтаксиса в редакторе работает отдельно, без SDK.", style = MaterialTheme.typography.bodySmall)
            SelectionContainer { Text(GO_SETUP, Modifier.padding(vertical = 12.dp), fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
            Text("Разрешите WexManager запуск команд Termux и доступ Termux к общему хранилищу.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { copyGoText(context,GO_SETUP) }) { Text("Копировать команды") }
            TextButton(onClick = { permission.launch(TERMUX_PERMISSION) }) { Text("Разрешить запуск команд") }
            TextButton(onClick = {
                val intent = context.packageManager.getLaunchIntentForPackage("com.termux") ?: Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/termux/termux-app/releases"))
                runCatching { context.startActivity(intent) }.onFailure { vm.message(it.message.orEmpty()) }
            }) { Text(if (vm.termuxInstalled()) "Открыть Termux" else "Установить Termux") }
            TextButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}"))) } }) { Text("Настройки приложения") }
        }
    }, confirmButton = { TextButton(onClick = { setup = false }) { Text("Готово") } })
    if (configOpen && project != null) GoConfigurationDialog(state.config,project,onDismiss = { configOpen = false }) { vm.configure(it); configOpen = false }
    valueAction?.let { action -> AlertDialog(onDismissRequest = { valueAction = null }, title = { Text(action.label) }, text = {
        GoTextField(value,onValueChange = { value = it },singleLine = true,label = { Text(if (action == GoAction.GET) "Модуль@версия" else "Имя модуля") })
    }, confirmButton = { Button(shapes = ButtonDefaults.shapes(), onClick = { onAction(action,value); valueAction = null }, enabled = value.isNotBlank()) { Text("Выполнить") } },
        dismissButton = { TextButton(onClick = { valueAction = null }) { Text("Отмена") } }) }
}

@Composable
private fun GoConfigurationDialog(initial: GoRunConfig, project: GoProject, onDismiss: () -> Unit, onSave: (GoRunConfig) -> Unit) {
    var config by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Параметры запуска Go") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(project.moduleRoot?.absolutePath ?: "Без go.mod · отдельный скрипт", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                listOf(GoRunScope.FILE to "Файл", GoRunScope.PACKAGE to "Пакет").forEachIndexed { index, (scope, label) ->
                    ToggleButton(checked = config.scope == scope, onCheckedChange = { config = config.copy(scope = scope) },
                        shapes = if (index == 0) ButtonGroupDefaults.connectedLeadingButtonShapes() else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { role = Role.RadioButton }) { Text(label) }
                }
            }
            Text("Пакет включает соседние .go-файлы; для cmd/server откройте файл из этой папки.", style = MaterialTheme.typography.bodySmall)
            GoTextField(config.arguments, onValueChange = { config = config.copy(arguments = it); error = null }, label = { Text("Аргументы программы") },
                supportingText = { Text("Кавычки поддерживаются: --name \"hello world\"") })
            GoTextField(config.workingDirectory, onValueChange = { config = config.copy(workingDirectory = it); error = null }, label = { Text("Рабочая папка") },
                supportingText = { Text("Пусто — ${project.directory.absolutePath}") })
            GoTextField(config.buildTags, onValueChange = { config = config.copy(buildTags = it); error = null }, label = { Text("Теги сборки") },
                supportingText = { Text("Например: android, integration") })
            GoTextField(config.testFlags, onValueChange = { config = config.copy(testFlags = it); error = null }, label = { Text("Флаги тестов") },
                supportingText = { Text("Например: -v -race -run TestName") })
            GoTextField(config.envVars, onValueChange = { config = config.copy(envVars = it); error = null }, label = { Text("Переменные окружения") },
                singleLine = false, supportingText = { Text("Одна на строку: GOOS=linux · CGO_ENABLED=0") })
            GoTextField(config.buildOutput, onValueChange = { config = config.copy(buildOutput = it); error = null }, label = { Text("Имя бинарника") },
                placeholder = { Text(projectBinaryName(project)) }, supportingText = { Text("Пусто — ${projectBinaryName(project)} в папке проекта") })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Подтягивать импорты перед запуском\ngo mod tidy (обновляет go.mod / go.sum)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Switch(checked = config.syncImports, onCheckedChange = { config = config.copy(syncImports = it) })
            }
            Text("Настройки сохраняются отдельно для каждого файла. Перед запуском сохраняются открытые изменённые файлы проекта.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = {
        Button(shapes = ButtonDefaults.shapes(), onClick = {
            runCatching {
                parseGoArguments(config.arguments)
                parseGoArguments(config.testFlags)
                parseGoTags(config.buildTags)
                parseGoEnv(config.envVars)
            }.onSuccess { onSave(config) }.onFailure { error = it.message }
        }) { Text("Сохранить") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

private fun copyGoText(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Go", text))
}
