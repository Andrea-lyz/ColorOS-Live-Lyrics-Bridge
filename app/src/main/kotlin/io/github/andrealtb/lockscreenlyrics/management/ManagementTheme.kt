package io.github.andrealtb.lockscreenlyrics.management

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import io.github.andrealtb.lockscreenlyrics.R

/** Spacing tokens: 8dp rhythm with 4dp fine steps, kept in one place (plan §8.5.2). */
internal object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    /** Readable content width on wide windows. */
    val maxContentWidth = 840.dp
}

/**
 * Fixed light theme built from the existing settings-page colors (res/values/colors.xml) so the
 * Views pages and the Compose pages share one palette. No dynamic color and no dark theme
 * (plan §8.3). onPrimary is the dark text color because the brand primary is a light amber.
 */
@Composable
internal fun ManagementTheme(content: @Composable () -> Unit) {
    val text = colorResource(R.color.settings_text)
    val textSecondary = colorResource(R.color.settings_text_secondary)
    val background = colorResource(R.color.settings_background)
    val surface = colorResource(R.color.settings_surface)
    val primary = colorResource(R.color.settings_primary)
    val primaryDeep = colorResource(R.color.settings_primary_deep)
    val secondary = colorResource(R.color.settings_secondary)
    val error = colorResource(R.color.settings_error)
    val scheme = lightColorScheme(
        primary = primaryDeep,
        onPrimary = text,
        primaryContainer = primary,
        onPrimaryContainer = text,
        secondary = secondary,
        onSecondary = Color.White,
        secondaryContainer = secondary.copy(alpha = 0.12f).compositeOver(surface),
        onSecondaryContainer = text,
        background = background,
        onBackground = text,
        surface = background,
        onSurface = text,
        onSurfaceVariant = textSecondary,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surface,
        error = error,
        onError = Color.White,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

private fun Color.compositeOver(background: Color): Color {
    val a = alpha
    return Color(
        red = red * a + background.red * (1f - a),
        green = green * a + background.green * (1f - a),
        blue = blue * a + background.blue * (1f - a),
        alpha = 1f,
    )
}
