package app.hitsu.vault.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val HitsuColorScheme = darkColorScheme(
    primary = HitsuColors.Accent,
    onPrimary = HitsuColors.Bg,
    primaryContainer = HitsuColors.BgElevated,
    onPrimaryContainer = HitsuColors.TextPrimary,
    inversePrimary = HitsuColors.AccentPress,
    secondary = HitsuColors.Accent,
    onSecondary = HitsuColors.Bg,
    secondaryContainer = HitsuColors.SurfaceInput,
    onSecondaryContainer = HitsuColors.TextPrimary,
    tertiary = HitsuColors.Accent,
    onTertiary = HitsuColors.Bg,
    tertiaryContainer = HitsuColors.SurfaceInput,
    onTertiaryContainer = HitsuColors.TextPrimary,
    background = HitsuColors.Bg,
    onBackground = HitsuColors.TextPrimary,
    surface = HitsuColors.Bg,
    onSurface = HitsuColors.TextPrimary,
    surfaceVariant = HitsuColors.SurfaceInput,
    onSurfaceVariant = HitsuColors.TextMuted,
    surfaceTint = HitsuColors.Bg,
    inverseSurface = HitsuColors.TextPrimary,
    inverseOnSurface = HitsuColors.Bg,
    error = HitsuColors.Danger,
    onError = HitsuColors.Bg,
    errorContainer = HitsuColors.BgElevated,
    onErrorContainer = HitsuColors.Danger,
    outline = HitsuColors.Stroke,
    outlineVariant = HitsuColors.Stroke,
    scrim = Color.Black,
    surfaceBright = HitsuColors.SurfaceInput,
    surfaceDim = HitsuColors.Bg,
    surfaceContainerLowest = HitsuColors.Bg,
    surfaceContainerLow = HitsuColors.BgElevated,
    surfaceContainer = HitsuColors.BgElevated,
    surfaceContainerHigh = HitsuColors.SurfaceInput,
    surfaceContainerHighest = HitsuColors.SurfaceInput,
)

@Composable
fun HitsuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HitsuColorScheme,
        shapes = HitsuShapes,
        typography = HitsuTypography,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides HitsuColors.TextPrimary,
            LocalTextStyle provides HitsuType.Body,
            content = content,
        )
    }
}
