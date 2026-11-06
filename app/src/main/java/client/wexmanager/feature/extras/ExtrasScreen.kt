package client.wexmanager.feature.extras

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

private data class Extra(val id: String, val title: String, val screen: @Composable (onBack: () -> Unit) -> Unit)

private val extras = listOf(
    Extra("text", "Статистика текста") { TextStatsScreen(it) },
    Extra("sum", "Контрольные суммы") { ChecksumScreen(it) },
    Extra("perm", "Разрешения приложения") { AppPermissionsScreen(it) },
    Extra("epoch", "Unix-время") { EpochScreen(it) },
    Extra("b64", "Base64") { Base64Screen(it) },
    Extra("json", "Формат JSON") { JsonPrettyScreen(it) },
    Extra("re", "Проверка регулярного выражения") { RegexScreen(it) },
    Extra("color", "Цвет из HEX") { ColorHexScreen(it) },
    Extra("case", "Регистр текста") { CaseScreen(it) },
    Extra("uuid", "Генератор UUID") { UuidScreen(it) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtrasScreen(onBack: () -> Unit) {
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    val tool = extras.firstOrNull { it.id == page }
    if (tool != null) {
        tool.screen { page = null }
        return
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мелочи") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(extras, key = { it.id }) { item ->
                ListItem(
                    headlineContent = { Text(item.title) },
                    modifier = Modifier.fillMaxWidth().clickable { page = item.id },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
