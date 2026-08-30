package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class TranslationActionPresentationPolicyTest {
    @Test
    public void enabledMatchesFullMediaControlBrightness() {
        assertEquals("io.github.andrealtb.lockscreenlyrics",
                TranslationActionPresentationPolicy.CANONICAL_ICON_PACKAGE);
        assertEquals(255, TranslationActionPresentationPolicy.imageAlpha(true));
        assertEquals("翻译：开启",
                TranslationActionPresentationPolicy.contentDescription(true));
    }

    @Test
    public void disabledUsesDimmedIconAndExplicitAccessibilityState() {
        assertEquals(135, TranslationActionPresentationPolicy.imageAlpha(false));
        assertEquals("翻译：关闭",
                TranslationActionPresentationPolicy.contentDescription(false));
    }

    @Test
    public void immediateReplacementKeepsSlotButInsetsGlyphToSixtyFivePercent() {
        assertEquals(0.175f,
                TranslationActionPresentationPolicy.ACTION_ICON_INSET_FRACTION,
                0.0001f);
        assertEquals(0.65f,
                1f - 2f * TranslationActionPresentationPolicy.ACTION_ICON_INSET_FRACTION,
                0.0001f);
    }
}
