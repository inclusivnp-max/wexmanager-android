package client.wexmanager.feature.network

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import client.wexmanager.core.network.RemoteConnection
import client.wexmanager.core.network.RemoteType
import client.wexmanager.ui.components.PolygonShape

val RemoteType.icon: ImageVector
    get() = when (this) {
        RemoteType.SFTP -> Icons.Rounded.Terminal
        RemoteType.FTP -> Icons.Rounded.Cloud
        RemoteType.FTPS -> Icons.Rounded.CloudDone
        RemoteType.SMB -> Icons.Rounded.Lan
        RemoteType.WEBDAV -> Icons.Rounded.Public
    }

val RemoteType.polygon: RoundedPolygon
    get() = when (this) {
        RemoteType.SFTP -> MaterialShapes.Cookie9Sided
        RemoteType.FTP -> MaterialShapes.Clover4Leaf
        RemoteType.FTPS -> MaterialShapes.Pentagon
        RemoteType.SMB -> MaterialShapes.Sunny
        RemoteType.WEBDAV -> MaterialShapes.Flower
    }

/** Значок типа подключения в фигуре Material Shapes. */
@Composable
fun TypeBadge(
    type: RemoteType,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    container: Color = typeContainer(type),
    content: Color = typeContent(type),
) {
    val shape = remember(type) { PolygonShape(type.polygon) }
    Box(modifier.size(size).clip(shape).background(container), contentAlignment = Alignment.Center) {
        Icon(type.icon, null, tint = content, modifier = Modifier.size(size * 0.48f))
    }
}

@Composable
fun typeContainer(type: RemoteType): Color = when (type) {
    RemoteType.SFTP -> MaterialTheme.colorScheme.primaryContainer
    RemoteType.FTP -> MaterialTheme.colorScheme.secondaryContainer
    RemoteType.FTPS -> MaterialTheme.colorScheme.tertiaryContainer
    RemoteType.SMB -> MaterialTheme.colorScheme.surfaceContainerHighest
    RemoteType.WEBDAV -> MaterialTheme.colorScheme.primaryFixedDim
}

@Composable
fun typeContent(type: RemoteType): Color = when (type) {
    RemoteType.SFTP -> MaterialTheme.colorScheme.onPrimaryContainer
    RemoteType.FTP -> MaterialTheme.colorScheme.onSecondaryContainer
    RemoteType.FTPS -> MaterialTheme.colorScheme.onTertiaryContainer
    RemoteType.SMB -> MaterialTheme.colorScheme.primary
    RemoteType.WEBDAV -> MaterialTheme.colorScheme.onPrimaryFixed
}

/** Крупное состояние экрана: фигура с «пружинным» появлением, иконка, текст и действия. */
@Composable
fun NetworkStateView(
    polygon: RoundedPolygon,
    icon: ImageVector,
    title: String,
    text: String?,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = remember(polygon) { PolygonShape(polygon) }
    val appear = remember { Animatable(0f) }
    val spatial = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) { appear.animateTo(1f, spatial) }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(136.dp)
                .graphicsLayer {
                    val p = appear.value
                    scaleX = 0.6f + 0.4f * p
                    scaleY = 0.6f + 0.4f * p
                    alpha = p.coerceIn(0f, 1f)
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = (1f - appear.value) * -45f }
                    .clip(shape)
                    .background(container),
            )
            Icon(icon, null, tint = content, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = scheme.onSurface, textAlign = TextAlign.Center)
        if (text != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 360.dp),
            )
        }
        if (extra != null) {
            Spacer(Modifier.height(16.dp))
            extra()
        }
        if (actions != null) {
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

/** Экран подключения: большой контейнерный индикатор и «дышащий» значок типа. */
@Composable
fun ConnectingView(connection: RemoteConnection?, modifier: Modifier = Modifier, actions: (@Composable RowScope.() -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    val pulse by rememberInfiniteTransition("pulse").animateFloat(
        initialValue = 0.92f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            ContainedLoadingIndicator(Modifier.size(132.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "Подключение…",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
        )
        if (connection != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .graphicsLayer { scaleX = pulse; scaleY = pulse }
                    .clip(RoundedCornerShape(50))
                    .background(scheme.surfaceContainerHigh)
                    .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TypeBadge(connection.type, size = 30.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "${connection.type.title} • ${connection.address}",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
        }
        if (actions != null) {
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
    }
}
