package client.wexmanager.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import client.wexmanager.core.settings.AnimationSpeed
import client.wexmanager.core.settings.AppSettings
import client.wexmanager.core.settings.PaletteStyleOption
import client.wexmanager.core.settings.ThemeMode
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme

val LocalAppSettings = staticCompositionLocalOf { AppSettings() }
val LocalIsDarkTheme = staticCompositionLocalOf { false }

@Composable
fun AppSettings.isDark(): Boolean = when (themeMode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

fun PaletteStyleOption.toKolor(): PaletteStyle = when (this) {
    PaletteStyleOption.TonalSpot -> PaletteStyle.TonalSpot
    PaletteStyleOption.Vibrant -> PaletteStyle.Vibrant
    PaletteStyleOption.Expressive -> PaletteStyle.Expressive
    PaletteStyleOption.Fidelity -> PaletteStyle.Fidelity
    PaletteStyleOption.Content -> PaletteStyle.Content
    PaletteStyleOption.Rainbow -> PaletteStyle.Rainbow
    PaletteStyleOption.FruitSalad -> PaletteStyle.FruitSalad
    PaletteStyleOption.Neutral -> PaletteStyle.Neutral
    PaletteStyleOption.Monochrome -> PaletteStyle.Monochrome
}

val dynamicColorSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun WexTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val dark = settings.isDark()
    val context = LocalContext.current
    val useSystemScheme = settings.dynamicColor && dynamicColorSupported &&
        settings.paletteStyle == PaletteStyleOption.TonalSpot && settings.contrast == 0f
    val systemSeed = if (settings.dynamicColor && dynamicColorSupported) {
        remember(context) { dynamicLightColorScheme(context).primary }
    } else null
    val seed = systemSeed ?: Color(settings.seedColor)

    val generated = rememberDynamicColorScheme(
        seedColor = seed,
        isDark = dark,
        isAmoled = settings.amoled,
        style = settings.paletteStyle.toKolor(),
        contrastLevel = settings.contrast.toDouble(),
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
    )
    val target = if (useSystemScheme) {
        val sys = remember(context, dark) { if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context) }
        if (settings.amoled && dark) sys.amoled() else sys
    } else generated

    val motion = remember(settings.animationSpeed) { scaledMotionScheme(settings.animationSpeed) }
    val colors = animatedColorScheme(target)

    CompositionLocalProvider(
        LocalAppSettings provides settings,
        LocalIsDarkTheme provides dark,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colors,
            motionScheme = motion,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

private fun ColorScheme.amoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0D),
    surfaceContainer = Color(0xFF111114),
    surfaceDim = Color.Black,
)

@Composable
private fun animatedColorScheme(target: ColorScheme): ColorScheme {
    val spec = tween<Color>(durationMillis = 420)
    @Composable
    fun Color.anim(): Color = animateColorAsState(this, spec, label = "scheme").value
    return target.copy(
        primary = target.primary.anim(),
        onPrimary = target.onPrimary.anim(),
        primaryContainer = target.primaryContainer.anim(),
        onPrimaryContainer = target.onPrimaryContainer.anim(),
        inversePrimary = target.inversePrimary.anim(),
        secondary = target.secondary.anim(),
        onSecondary = target.onSecondary.anim(),
        secondaryContainer = target.secondaryContainer.anim(),
        onSecondaryContainer = target.onSecondaryContainer.anim(),
        tertiary = target.tertiary.anim(),
        onTertiary = target.onTertiary.anim(),
        tertiaryContainer = target.tertiaryContainer.anim(),
        onTertiaryContainer = target.onTertiaryContainer.anim(),
        background = target.background.anim(),
        onBackground = target.onBackground.anim(),
        surface = target.surface.anim(),
        onSurface = target.onSurface.anim(),
        surfaceVariant = target.surfaceVariant.anim(),
        onSurfaceVariant = target.onSurfaceVariant.anim(),
        surfaceTint = target.surfaceTint.anim(),
        inverseSurface = target.inverseSurface.anim(),
        inverseOnSurface = target.inverseOnSurface.anim(),
        outline = target.outline.anim(),
        outlineVariant = target.outlineVariant.anim(),
        surfaceBright = target.surfaceBright.anim(),
        surfaceDim = target.surfaceDim.anim(),
        surfaceContainer = target.surfaceContainer.anim(),
        surfaceContainerHigh = target.surfaceContainerHigh.anim(),
        surfaceContainerHighest = target.surfaceContainerHighest.anim(),
        surfaceContainerLow = target.surfaceContainerLow.anim(),
        surfaceContainerLowest = target.surfaceContainerLowest.anim(),
    )
}

/** Масштабирует пружины Expressive-моушена под выбранную скорость анимаций. */
@Stable
private fun scaledMotionScheme(speed: AnimationSpeed): MotionScheme {
    val base = MotionScheme.expressive()
    val factor = when (speed) {
        AnimationSpeed.Off -> return NoMotionScheme
        AnimationSpeed.Fast -> 1.8f
        AnimationSpeed.Normal -> return base
        AnimationSpeed.Relaxed -> 0.55f
    }
    return object : MotionScheme {
        private fun <T> FiniteAnimationSpec<T>.scale(): FiniteAnimationSpec<T> =
            if (this is SpringSpec<T>) spring(dampingRatio, stiffness * factor * factor, visibilityThreshold) else this

        override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = base.defaultSpatialSpec<T>().scale()
        override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = base.fastSpatialSpec<T>().scale()
        override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = base.slowSpatialSpec<T>().scale()
        override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = base.defaultEffectsSpec<T>().scale()
        override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = base.fastEffectsSpec<T>().scale()
        override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = base.slowEffectsSpec<T>().scale()
    }
}

private object NoMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}
