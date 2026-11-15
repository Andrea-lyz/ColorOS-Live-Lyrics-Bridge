package io.github.andrealtb.lockscreenlyrics

import io.github.andrealtb.lockscreenlyrics.management.appearance.AppearanceValue
import io.github.andrealtb.lockscreenlyrics.management.appearance.appearanceGroups
import io.github.andrealtb.lockscreenlyrics.management.appearance.artworkValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lives in the config's package: LyricUiConfig is package-private. */
class AppearanceSnapshotTest {
    private fun lines(s: AppearanceSnapshot): Map<Int, AppearanceValue> =
        appearanceGroups(s).flatMap { it.lines }.associate { it.label to it.value }

    @Test fun defaultsReadAsTheDefaultPreset() {
        val s = AppearanceSnapshot(LyricUiConfig.defaults(), AppearanceSnapshot.Artwork.OFF, "ignored")
        assertEquals(AppearanceSnapshot.Preset.DEFAULT, s.preset)
        assertNull(s.artworkProvider)
        // Same order as the reorganised old page (§14.18).
        assertEquals(listOf(R.string.settings_section_color, R.string.settings_section_typography,
            R.string.appearance_section_effects, R.string.settings_section_motion_refresh, R.string.settings_section_screen),
            appearanceGroups(s).map { it.title })
        assertEquals(AppearanceValue.Res(R.string.appearance_screen_always), lines(s)[R.string.setting_screen_timeout])
        assertEquals(AppearanceValue.Res(R.string.refresh_follow_screen), lines(s)[R.string.setting_refresh_limit])
        assertEquals(AppearanceValue.Res(R.string.artwork_settings_link_sub_off), artworkValue(s))
    }

    @Test fun editedValuesShowAsSavedAndFormatArgumentsMatchTheirPlaceholders() {
        val config = LyricUiConfig.defaults().buildUpon()
            .mainFontTenthsSp(245)
            .fontWeight(LyricUiConfig.WEIGHT_BOLD)
            .maxRefreshRateHz(120)
            .glowIntensityPercent(0)
            .build()
        val s = AppearanceSnapshot(config, AppearanceSnapshot.Artwork.READY, "Salt")
        assertEquals(AppearanceSnapshot.Preset.CUSTOM, s.preset)
        val l = lines(s)
        assertEquals(AppearanceValue.Format(R.string.appearance_sp, "24.5"), l[R.string.setting_font_size])
        assertEquals(AppearanceValue.Res(R.string.weight_bold), l[R.string.setting_font_weight])
        // refresh_hz_format and appearance_percent are %d: the argument must be an Int.
        assertEquals(AppearanceValue.Format(R.string.refresh_hz_format, 120), l[R.string.setting_refresh_limit])
        assertTrue((l[R.string.appearance_translation_ratio] as AppearanceValue.Format).arg is Int)
        assertEquals(AppearanceValue.Res(R.string.visual_state_off), l[R.string.appearance_glow])
        assertEquals(AppearanceValue.Format(R.string.artwork_settings_link_sub_on, "Salt"), artworkValue(s))
    }

    @Test fun glowShowsItsColourOrFollowsTheCover() {
        val own = AppearanceSnapshot(LyricUiConfig.defaults(), null, null)
        assertTrue(lines(own)[R.string.appearance_glow] is AppearanceValue.Color)
        val cover = AppearanceSnapshot(LyricUiConfig.defaults().buildUpon().glowFollowsCover(true).build(), null, null)
        assertEquals(AppearanceValue.Res(R.string.setting_glow_follow_cover), lines(cover)[R.string.appearance_glow])
        assertEquals(AppearanceSnapshot.Artwork.OFF, cover.artwork)
    }

    @Test fun screenTimeoutShowsOffOrItsSeconds() {
        val off = AppearanceSnapshot(LyricUiConfig.defaults().buildUpon().screenTimeoutEnabled(false).build(), null, null)
        assertEquals(AppearanceValue.Res(R.string.visual_state_off), lines(off)[R.string.setting_screen_timeout])
        val timed = AppearanceSnapshot(LyricUiConfig.defaults().buildUpon().screenTimeoutSeconds(90).build(), null, null)
        assertEquals(AppearanceValue.Format(R.string.appearance_seconds, 90), lines(timed)[R.string.setting_screen_timeout])
    }

    @Test fun aProviderProblemDropsTheLabel() {
        val s = AppearanceSnapshot(LyricUiConfig.defaults(), AppearanceSnapshot.Artwork.PROBLEM, "Salt")
        assertNull(s.artworkProvider)
        assertEquals(AppearanceValue.Res(R.string.artwork_settings_link_sub_problem), artworkValue(s))
    }
}
