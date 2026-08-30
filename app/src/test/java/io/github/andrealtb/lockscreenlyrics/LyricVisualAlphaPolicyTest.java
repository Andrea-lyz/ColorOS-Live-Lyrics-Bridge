package io.github.andrealtb.lockscreenlyrics;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class LyricVisualAlphaPolicyTest {
    @Test
    public void defaultsResolvePhaseSixAlphaBaseline() {
        LyricUiConfig config = LyricUiConfig.defaults();

        assertEquals(255, LyricVisualAlphaPolicy.activeAlpha(config));
        assertEquals(128, LyricVisualAlphaPolicy.currentUnrevealedAlpha(config));
        assertEquals(153, LyricVisualAlphaPolicy.activeTranslationAlpha(config));
        assertEquals(204, LyricVisualAlphaPolicy.activeTranslationProgressAlpha(config));
        assertEquals(112, LyricVisualAlphaPolicy.inactiveMainAlpha(config));
        assertEquals(112, LyricVisualAlphaPolicy.inactiveTranslationAlpha(config));
        assertEquals(112, LyricVisualAlphaPolicy.steadyInactiveMainAlpha(config));
        assertEquals(112, LyricVisualAlphaPolicy.steadyInactiveTranslationAlpha(config));
    }

    @Test
    public void inactiveTranslationCanFollowOrUseItsIndependentValue() {
        LyricUiConfig follows = LyricUiConfig.defaults().buildUpon()
                .inactiveOpacityPercent(73)
                .inactiveTranslationFollowsMain(true)
                .inactiveTranslationOpacityPercent(31)
                .build();
        LyricUiConfig independent = follows.buildUpon()
                .inactiveTranslationFollowsMain(false)
                .build();

        assertEquals(186, LyricVisualAlphaPolicy.inactiveTranslationAlpha(follows));
        assertEquals(79, LyricVisualAlphaPolicy.inactiveTranslationAlpha(independent));
        assertEquals(31, independent.inactiveTranslationOpacityPercent);
    }

    @Test
    public void rowFadeMultipliesResolvedLaneAlphaExactlyOnce() {
        LyricUiConfig config = LyricUiConfig.defaults().buildUpon()
                .inactiveOpacityPercent(44)
                .inactiveTranslationFollowsMain(false)
                .inactiveTranslationOpacityPercent(60)
                .inactiveRowFadeEnabled(true)
                .inactiveRowFadePercent(90)
                .build();

        assertEquals(101, LyricVisualAlphaPolicy.steadyInactiveMainAlpha(config));
        assertEquals(138, LyricVisualAlphaPolicy.steadyInactiveTranslationAlpha(config));
        assertEquals(90, LyricVisualAlphaPolicy.applyInactiveRowFade(config, 100));
        assertEquals(0.9f, LyricVisualAlphaPolicy.inactiveRowFadeMultiplier(config), 0f);
        assertEquals(39.6078f, LyricVisualAlphaPolicy.steadyInactiveMainPercent(config), 0.0001f);
        assertEquals(
                54.1176f,
                LyricVisualAlphaPolicy.steadyInactiveTranslationPercent(config),
                0.0001f);
    }

    @Test
    public void nullAndOutOfRangeInputsAreSafe() {
        assertEquals(255, LyricVisualAlphaPolicy.activeAlpha(null));
        assertEquals(0, LyricVisualAlphaPolicy.percentAlpha(-1));
        assertEquals(255, LyricVisualAlphaPolicy.percentAlpha(101));
        assertEquals(0, LyricVisualAlphaPolicy.applyInactiveRowFade(null, -1));
        assertEquals(255, LyricVisualAlphaPolicy.applyInactiveRowFade(null, 300));
        assertEquals(1f, LyricVisualAlphaPolicy.inactiveRowFadeMultiplier(null), 0f);
    }
}
