package io.github.andrealtb.lockscreenlyrics.management.appearance

import androidx.annotation.StringRes
import io.github.andrealtb.lockscreenlyrics.AppearanceSnapshot
import io.github.andrealtb.lockscreenlyrics.R
import java.util.Locale

/** Where a card on the Appearance tab leads. Only the old pages edit anything (plan §14.18). */
internal enum class AppearanceTarget { SETTINGS, ARTWORK, TRANSLATION, CLEANUP, BACKUP }

internal sealed interface AppearanceValue {
    data class Res(@param:StringRes val id: Int) : AppearanceValue
    data class Format(@param:StringRes val id: Int, val arg: Any) : AppearanceValue
    /** A colour swatch plus its hex text. */
    data class Color(val hex: String) : AppearanceValue
}

internal data class AppearanceLine(@param:StringRes val label: Int, val value: AppearanceValue)

/** One section of the single lyric-appearance card; all of it is edited on the old page. */
internal data class AppearanceGroup(@param:StringRes val title: Int, val lines: List<AppearanceLine>)

@StringRes internal fun presetLabel(preset: AppearanceSnapshot.Preset): Int = when (preset) {
    AppearanceSnapshot.Preset.DEFAULT -> R.string.preset_default
    AppearanceSnapshot.Preset.SOFT -> R.string.preset_soft
    AppearanceSnapshot.Preset.VIVID -> R.string.preset_vivid
    AppearanceSnapshot.Preset.MINIMAL -> R.string.preset_minimal
    AppearanceSnapshot.Preset.CUSTOM -> R.string.preset_custom
}

@StringRes internal fun presetSummary(preset: AppearanceSnapshot.Preset): Int = when (preset) {
    AppearanceSnapshot.Preset.DEFAULT -> R.string.preset_default_summary
    AppearanceSnapshot.Preset.SOFT -> R.string.preset_soft_summary
    AppearanceSnapshot.Preset.VIVID -> R.string.preset_vivid_summary
    AppearanceSnapshot.Preset.MINIMAL -> R.string.preset_minimal_summary
    AppearanceSnapshot.Preset.CUSTOM -> R.string.preset_custom_summary
}

private fun onOff(on: Boolean) = AppearanceValue.Res(if (on) R.string.visual_state_on else R.string.visual_state_off)

/** The sections of the lyric-appearance card, in the old page's section order. */
internal fun appearanceGroups(s: AppearanceSnapshot): List<AppearanceGroup> = listOf(
    AppearanceGroup(R.string.settings_section_color, listOf(
        AppearanceLine(R.string.appearance_primary_color, AppearanceValue.Color(s.primaryColor)),
        AppearanceLine(R.string.appearance_glow, when {
            !s.glowOn -> onOff(false)
            s.glowFollowsCover -> AppearanceValue.Res(R.string.setting_glow_follow_cover)
            else -> AppearanceValue.Color(s.glowColor)
        }),
        AppearanceLine(R.string.setting_blur, onOff(s.blurOn)),
        AppearanceLine(R.string.setting_scroll_scale, onOff(s.scaleOn)),
    )),
    AppearanceGroup(R.string.settings_section_typography, listOf(
        AppearanceLine(R.string.setting_font_size,
            AppearanceValue.Format(R.string.appearance_sp, String.format(Locale.ROOT, "%.1f", s.mainFontTenthsSp / 10f))),
        AppearanceLine(R.string.appearance_translation_ratio,
            AppearanceValue.Format(R.string.appearance_percent, s.translationFontRatioPercent)),
        AppearanceLine(R.string.setting_font_weight, AppearanceValue.Res(when (s.weight) {
            AppearanceSnapshot.Weight.SYSTEM -> R.string.weight_system
            AppearanceSnapshot.Weight.REGULAR -> R.string.weight_regular
            AppearanceSnapshot.Weight.MEDIUM -> R.string.weight_medium
            AppearanceSnapshot.Weight.BOLD -> R.string.weight_bold
        })),
        AppearanceLine(R.string.setting_alignment, AppearanceValue.Res(when (s.alignment) {
            AppearanceSnapshot.Alignment.START -> R.string.align_start
            AppearanceSnapshot.Alignment.CENTER -> R.string.align_center
            AppearanceSnapshot.Alignment.END -> R.string.align_end
        })),
    )),
    AppearanceGroup(R.string.appearance_section_effects, listOf(
        AppearanceLine(R.string.setting_line_progress, onOff(s.lineProgressOn)),
        AppearanceLine(R.string.setting_translation_progress, onOff(s.translationProgressOn)),
        AppearanceLine(R.string.setting_char_lift, onOff(s.charLiftOn)),
    )),
    AppearanceGroup(R.string.settings_section_motion_refresh, listOf(
        AppearanceLine(R.string.setting_motion, AppearanceValue.Res(when (s.motion) {
            AppearanceSnapshot.Motion.STANDARD -> R.string.motion_standard
            AppearanceSnapshot.Motion.REDUCED -> R.string.motion_reduced
            AppearanceSnapshot.Motion.OFF -> R.string.motion_off
        })),
        AppearanceLine(R.string.setting_refresh_limit,
            if (s.maxRefreshRateHz > 0) AppearanceValue.Format(R.string.refresh_hz_format, s.maxRefreshRateHz)
            else AppearanceValue.Res(R.string.refresh_follow_screen)),
    )),
    AppearanceGroup(R.string.settings_section_screen, listOf(
        AppearanceLine(R.string.setting_screen_timeout, when {
            !s.screenTimeoutOn -> onOff(false)
            s.screenTimeoutSeconds > 0 -> AppearanceValue.Format(R.string.appearance_seconds, s.screenTimeoutSeconds)
            else -> AppearanceValue.Res(R.string.appearance_screen_always)
        }),
    )),
)

/** The dynamic-artwork card's one line, matching the old page's artwork row. */
internal fun artworkValue(s: AppearanceSnapshot): AppearanceValue = when (s.artwork) {
    AppearanceSnapshot.Artwork.OFF -> AppearanceValue.Res(R.string.artwork_settings_link_sub_off)
    AppearanceSnapshot.Artwork.READY -> AppearanceValue.Format(R.string.artwork_settings_link_sub_on, s.artworkProvider.orEmpty())
    AppearanceSnapshot.Artwork.PROBLEM -> AppearanceValue.Res(R.string.artwork_settings_link_sub_problem)
}
