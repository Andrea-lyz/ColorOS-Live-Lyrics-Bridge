package io.github.andrealtb.lockscreenlyrics;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LyricVisualLayersSettingsContractTest {
    @Test
    public void manifestAndMainPageExposeOneIndependentVisualOwner() throws Exception {
        String manifest = readProjectFile("app/src/main/AndroidManifest.xml");
        String main = readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/LyricUiSettingsActivity.java");

        assertTrue(manifest.contains(".LyricVisualLayersSettingsActivity"));
        assertTrue(manifest.contains("@string/visual_layers_settings_title"));
        assertTrue(main.contains("LyricVisualLayersSettingsActivity.class"));
        assertTrue(main.contains("R.string.link_visual_layers_summary"));
        assertTrue(main.contains("updatePresetCards(LyricUiPreset.detect(draft))"));
        assertFalse(main.contains("private Slider opacity;"));
        assertFalse(main.contains("inactiveOpacityPercent(materialProgress(opacity))"));
    }

    @Test
    public void subpageOwnsEveryVisualFieldAndUsesCanonicalApplyAck() throws Exception {
        String activity = readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/"
                        + "LyricVisualLayersSettingsActivity.java");
        String[] ownedFields = {
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
                "inactiveRowFadePercent"
        };
        for (String field : ownedFields) {
            assertTrue("Missing visual owner " + field, activity.contains("." + field));
        }
        assertTrue(activity.contains("LyricUiConfigRepository.save(preferences, config)"));
        assertTrue(activity.contains("LyricUiConfigRepository.putSnapshot("));
        assertTrue(activity.contains("LyricUiSettings.SOURCE_VISUAL_LAYERS"));
        assertTrue(activity.contains("VisualLayersApplyResultReceiver"));
        assertTrue(activity.contains("RESULT_CONFIG_REVISION"));
    }

    @Test
    public void previewUsesSharedPaletteAndAlphaPolicyForAllVisualLayers() throws Exception {
        String preview = readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/"
                        + "LyricVisualLayersPreviewView.java");

        assertTrue(preview.contains("LyricUiPalette.from(config)"));
        assertTrue(preview.contains("LyricVisualAlphaPolicy.steadyInactiveMainAlpha(config)"));
        assertTrue(preview.contains(
                "LyricVisualAlphaPolicy.steadyInactiveTranslationAlpha(config)"));
        assertTrue(preview.contains("palette.focusedInactive"));
        assertTrue(preview.contains("palette.activeTranslationProgress"));
        assertTrue(preview.contains("config.verticalFadeLengthTenthsDp / 10f"));
        assertTrue(preview.contains("PorterDuff.Mode.DST_IN"));
    }

    @Test
    public void visualStringsExistInBothLocales() throws Exception {
        String chinese = readProjectFile("app/src/main/res/values/strings.xml");
        String english = readProjectFile("app/src/main/res/values-en/strings.xml");
        String[] keys = {
                "visual_layers_settings_title",
                "link_visual_layers_summary",
                "visual_active_translation_progress_opacity",
                "visual_inactive_translation_follows_main",
                "visual_vertical_fade_length",
                "visual_inactive_row_fade_opacity",
                "visual_value_effective"
        };
        for (String key : keys) {
            assertTrue(chinese.contains("name=\"" + key + "\""));
            assertTrue(english.contains("name=\"" + key + "\""));
        }
    }

    @Test
    public void devicePolishUsesThemedDialogCompactPreviewFullDockAndLongSliders()
            throws Exception {
        String activity = readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/"
                        + "LyricVisualLayersSettingsActivity.java");
        String preview = readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/"
                        + "LyricVisualLayersPreviewView.java");
        String base = readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/"
                        + "SettingsBaseActivity.java");

        assertTrue(activity.contains("showSettingsDiscardDialog(this::finish)"));
        assertFalse(activity.contains("new AlertDialog.Builder"));
        assertTrue(base.contains("showSettingsDiscardDialog(Runnable onDiscard)"));
        assertTrue(activity.contains("body.setPadding(0, screenPadding, 0, 0)"));
        assertTrue(activity.contains("controlsStage.addView(settingsBottomAction(saveButton)"));
        assertTrue(activity.contains("slider.setContentDescription(label)"));
        assertTrue(activity.contains("LinearLayout.LayoutParams.MATCH_PARENT,\n                dp(38)"));
        assertTrue(activity.contains(
                "getString(R.string.visual_vertical_fade_length), 0f, 120f, 1f"));
        assertTrue(activity.contains("verticalFadeLengthTouched"));
        assertTrue(preview.contains("int desiredHeight = Math.round(dp(196f))"));
        assertTrue(preview.contains("float top = dp(19f)"));
        assertTrue(preview.contains("top + dp(75f)"));
        assertTrue(preview.contains("top + dp(133f)"));
    }

    private static String readProjectFile(String relativePath) throws Exception {
        File direct = new File(relativePath);
        File file = direct.isFile()
                ? direct
                : new File(".." + File.separator + relativePath);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}
