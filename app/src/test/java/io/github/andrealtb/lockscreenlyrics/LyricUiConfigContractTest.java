package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class LyricUiConfigContractTest {
    private static final List<String> FIELDS = Arrays.asList(
            "activeOpacityPercent",
            "currentUnrevealedOpacityPercent",
            "activeTranslationOpacityPercent",
            "activeTranslationProgressOpacityPercent",
            "inactiveOpacityPercent",
            "inactiveTranslationFollowsMain",
            "inactiveTranslationOpacityPercent",
            "verticalFadeEnabled",
            "verticalFadeLengthTenthsDp",
            "inactiveRowFadeEnabled",
            "inactiveRowFadePercent",
            "blurEnabled",
            "blurRadiusTenthsPx",
            "scaleEnabled",
            "inactiveScalePercent",
            "glowEnabled",
            "glowIntensityPercent",
            "glowRadiusPercent",
            "primaryColor",
            "glowColor",
            "motionMode",
            "passiveVerticalPanEnabled",
            "translationMarqueeEnabled",
            "maxRefreshRateHz",
            "defaultTranslationEnabled",
            "lineTimedProgressEnabled",
            "translationProgressEnabled",
            "screenTimeoutEnabled",
            "screenTimeoutSeconds",
            "mainFontTenthsSp",
            "translationFontRatioPercent",
            "fontWeight",
            "alignment",
            "lineSpacingTenthsDp",
            "wrappedLineSpacingTenthsDp");
    @Test
    public void everySchemaThreeFieldRoundTripsThroughCanonicalCodecMap() {
        LyricUiConfig source = LyricUiConfig.defaults().buildUpon()
                .activeOpacityPercent(96)
                .currentUnrevealedOpacityPercent(47)
                .activeTranslationOpacityPercent(58)
                .activeTranslationProgressOpacityPercent(79)
                .inactiveOpacityPercent(63)
                .inactiveTranslationFollowsMain(false)
                .inactiveTranslationOpacityPercent(42)
                .verticalFadeEnabled(false)
                .verticalFadeLengthTenthsDp(731)
                .inactiveRowFadeEnabled(true)
                .inactiveRowFadePercent(84)
                .blurEnabled(true)
                .blurRadiusTenthsPx(55)
                .scaleEnabled(true)
                .inactiveScalePercent(87)
                .glowEnabled(true)
                .glowIntensityPercent(65)
                .glowRadiusPercent(21)
                .primaryColor("#123456")
                .glowColor("#654321")
                .motionMode(LyricUiConfig.MOTION_REDUCED)
                .passiveVerticalPanEnabled(false)
                .translationMarqueeEnabled(false)
                .maxRefreshRateHz(90)
                .defaultTranslationEnabled(false)
                .lineTimedProgressEnabled(true)
                .translationProgressEnabled(true)
                .screenTimeoutEnabled(false)
                .screenTimeoutSeconds(120)
                .mainFontTenthsSp(245)
                .translationFontRatioPercent(70)
                .fontWeight(LyricUiConfig.WEIGHT_MEDIUM)
                .alignment(LyricUiConfig.ALIGN_CENTER)
                .lineSpacingTenthsDp(35)
                .wrappedLineSpacingTenthsDp(25)
                .build();

        Map<String, Object> encoded = LyricUiConfigCodec.encode(source);
        LyricUiConfig decoded = LyricUiConfigCodec.decode(
                encoded,
                LyricUiConfig.defaults(),
                false);

        assertEquals(FIELDS.size() + 1, encoded.size());
        assertNotNull(decoded);
        assertEquals(source, decoded);
        assertEquals(source.hashCode(), decoded.hashCode());
    }

    @Test
    public void everyConfigFieldHasAnExplicitRuntimeOrUiConsumer() throws Exception {
        String sources = readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/LockscreenLyricsModule.java")
                + readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/LyricUiSettingsActivity.java")
                + readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/LyricUiColors.java")
                + readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/LyricUiLayoutPolicy.java")
                + readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/LyricVisualAlphaPolicy.java");
        for (String field : FIELDS) {
            assertTrue("Missing explicit config consumer for " + field,
                    sources.contains("." + field));
        }
    }

    @Test
    public void rendererConsumesVisualSchemaWithoutLegacyFadeCoupling() throws Exception {
        String module = readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/LockscreenLyricsModule.java");
        String colors = readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/LyricUiColors.java");
        String renderConstants = readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/render/WordLyricRenderConstants.java");

        assertTrue(module.contains(
                "setVerticalFadingEdgeEnabled(config.verticalFadeEnabled)"));
        assertTrue(module.contains("config.verticalFadeLengthTenthsDp / 10f"));
        assertTrue(module.contains("if (!uiConfig.inactiveRowFadeEnabled)"));
        assertTrue(module.contains("inactiveRowFadeMultiplier(uiConfig)"));
        assertTrue(module.contains(
                "featherPaint.setAlpha((revealPaint.getColor() >>> 24) & 0xFF)"));
        assertTrue(colors.contains("LyricVisualAlphaPolicy.activeAlpha(config)"));
        assertTrue(colors.contains("LyricVisualAlphaPolicy.inactiveTranslationAlpha(config)"));
        assertFalse(module.contains("OFFICIAL_LYRIC_INACTIVE_ROW_FADE"));
        assertFalse(renderConstants.contains("OFFICIAL_LYRIC_INACTIVE_ROW_FADE"));
    }

    private static String readProjectFile(String relativePath) throws Exception {
        File direct = new File(relativePath);
        File file = direct.isFile()
                ? direct
                : new File(".." + File.separator + relativePath);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
