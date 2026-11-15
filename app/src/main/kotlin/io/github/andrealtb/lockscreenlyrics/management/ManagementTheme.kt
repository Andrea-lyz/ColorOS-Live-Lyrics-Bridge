package io.github.andrealtb.lockscreenlyrics.management

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
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
 * (plan §8.3).
 *
 * MD3 audit (§14.19): `primary` is what Material draws text, outlines, progress and switch
 * tracks with, so it is the deep gold (5.9:1 on white), not the brand amber (1.7:1, which made
 * outlined/text button labels unreadable). The amber stays as `primaryContainer` and as the fill
 * of [BrandButton]. Roles the scheme used to leave at the purple baseline are set explicitly.
 */
@Composable
internal fun ManagementTheme(content: @Composable () -> Unit) {
    val text = colorResource(R.color.settings_text)
    val textSecondary = colorResource(R.color.settings_text_secondary)
    val background = colorResource(R.color.settings_background)
    val surface = colorResource(R.color.settings_surface)
    val primary = colorResource(R.color.settings_primary)
    val primaryText = colorResource(R.color.settings_primary_text)
    val secondary = colorResource(R.color.settings_secondary)
    val error = colorResource(R.color.settings_error)
    val scheme = lightColorScheme(
        primary = primaryText,
        onPrimary = Color.White,
        primaryContainer = primary,
        onPrimaryContainer = text,
        inversePrimary = primary,
        surfaceTint = primaryText,
        secondary = secondary,
        onSecondary = Color.White,
        secondaryContainer = secondary.copy(alpha = 0.12f).compositeOver(surface),
        onSecondaryContainer = text,
        background = background,
        onBackground = text,
        surface = background,
        onSurface = text,
        onSurfaceVariant = textSecondary,
        surfaceVariant = Neutral.surfaceVariant,
        outline = Neutral.outline,
        outlineVariant = Neutral.outlineVariant,
        inverseSurface = Neutral.inverseSurface,
        inverseOnSurface = Neutral.inverseOnSurface,
        tertiary = secondary,
        onTertiary = Color.White,
        tertiaryContainer = secondary.copy(alpha = 0.12f).compositeOver(surface),
        onTertiaryContainer = text,
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

/** Neutral greys for the roles colors.xml has no entry for (no purple tint from the baseline). */
private object Neutral {
    val surfaceVariant = Color(0xFFE8EAED)
    val outline = Color(0xFF747775)
    val outlineVariant = Color(0xFFC4C7C5)
    val inverseSurface = Color(0xFF2F3033)
    val inverseOnSurface = Color(0xFFF1F1F1)
}

/**
 * The main action button in the brand amber with dark text (11:1), matching the old settings
 * pages' save button. Use it where Material's filled `Button` would go.
 */
@Composable
internal fun BrandButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = colorResource(R.color.settings_primary_deep),
            contentColor = colorResource(R.color.settings_text),
        ),
        content = content,
    )
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
