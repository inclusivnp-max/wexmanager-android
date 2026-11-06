package client.wexmanager.feature.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.SwipeRight
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.graphics.shapes.Morph
import client.wexmanager.core.util.StoragePermission
import client.wexmanager.ui.components.MorphShape

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var storageGranted by remember { mutableStateOf(StoragePermission.isGranted(context)) }
    var notifGranted by remember { mutableStateOf(StoragePermission.notificationsGranted(context)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        storageGranted = StoragePermission.isGranted(context)
        notifGranted = StoragePermission.notificationsGranted(context)
    }
    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        storageGranted = StoragePermission.isGranted(context)
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifGranted = it
    }
    val scheme = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxSize()
            .background(scheme.surface)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(36.dp))
        MorphingLogo()
        Spacer(Modifier.height(28.dp))
        Text("WexManager", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Мощный двухпанельный файловый менеджер",
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Feature(Icons.Rounded.ViewColumn, "Две панели", "Копируйте и перемещайте файлы между панелями в одно касание")
        Feature(Icons.Rounded.SwipeRight, "Жесты", "Проведите вправо по иконке и ведите вниз — выделение диапазона")
        Feature(Icons.Rounded.Palette, "Ваш стиль", "Темы, палитры, формы и иконки для каждого типа файлов")
        Spacer(Modifier.height(24.dp))

        PermissionCard(
            icon = Icons.Rounded.FolderOpen,
            title = "Доступ к файлам",
            text = "Нужен, чтобы просматривать и изменять файлы во всей памяти устройства. Папки Android/data и Android/obb даже после полного доступа открываются отдельно в приложении кнопкой «Разрешить».",
            granted = storageGranted,
            buttonText = "Выдать доступ",
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { context.startActivity(StoragePermission.manageAllFilesIntent(context)) }
                    .onFailure { context.startActivity(StoragePermission.fallbackIntent()) }
            } else legacyLauncher.launch(StoragePermission.legacyPermissions)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            Spacer(Modifier.height(12.dp))
            PermissionCard(
                icon = Icons.Rounded.Notifications,
                title = "Уведомления",
                text = "Прогресс копирования и распаковки в шторке. Можно пропустить.",
                granted = notifGranted,
                buttonText = "Разрешить",
            ) { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onDone,
            enabled = storageGranted,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shapes = ButtonDefaults.shapes(),
        ) { Text("Начать", style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MorphingLogo() {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "logo")
    val progress by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "morph",
    )
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "rot")
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Clover8Leaf) }
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(148.dp)
                .graphicsLayer { rotationZ = rotation }
                .clip(MorphShape(morph, progress))
                .background(scheme.primaryContainer),
        )
        Icon(Icons.Rounded.Folder, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(64.dp))
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PermissionCard(icon: ImageVector, title: String, text: String, granted: Boolean, buttonText: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // В тёмной теме Expresssive primaryContainer — светлая заливка. Текст и значок берут onPrimaryContainer.
    val container = if (granted) scheme.primaryContainer else scheme.surfaceContainerHigh
    val titleColor = if (granted) scheme.onPrimaryContainer else scheme.onSurface
    val bodyColor = if (granted) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = container,
        contentColor = titleColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (granted) Icons.Rounded.CheckCircle else icon, null, tint = bodyColor)
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = bodyColor)
            if (!granted) {
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = onClick, shapes = ButtonDefaults.shapes()) { Text(buttonText) }
            }
        }
    }
}
