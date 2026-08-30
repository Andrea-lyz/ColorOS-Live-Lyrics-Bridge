package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LyricUiConfigOwnerPolicyTest {
    @Test
    public void externalSubpageFieldsMergeWithoutOverwritingMainPageDraft() {
        LyricUiConfig draft = LyricUiConfig.defaults().buildUpon()
                .inactiveOpacityPercent(63)
                .primaryColor("#123456")
                .blurEnabled(false)
                .defaultTranslationEnabled(true)
                .build();
        LyricUiConfig persisted = LyricUiConfig.defaults().buildUpon()
                .inactiveOpacityPercent(31)
                .activeOpacityPercent(87)
                .currentUnrevealedOpacityPercent(43)
                .activeTranslationOpacityPercent(52)
                .activeTranslationProgressOpacityPercent(74)
                .inactiveTranslationFollowsMain(false)
                .inactiveTranslationOpacityPercent(28)
                .verticalFadeEnabled(false)
                .verticalFadeLengthTenthsDp(777)
                .inactiveRowFadeEnabled(true)
                .inactiveRowFadePercent(76)
                .primaryColor("#ABCDEF")
                .blurEnabled(true)
                .defaultTranslationEnabled(false)
                .build();

        LyricUiConfig merged = LyricUiConfigOwnerPolicy.mergeExternalFields(draft, persisted);

        assertEquals(31, merged.inactiveOpacityPercent);
        assertEquals(87, merged.activeOpacityPercent);
        assertEquals(43, merged.currentUnrevealedOpacityPercent);
        assertEquals(52, merged.activeTranslationOpacityPercent);
        assertEquals(74, merged.activeTranslationProgressOpacityPercent);
        assertFalse(merged.inactiveTranslationFollowsMain);
        assertEquals(28, merged.inactiveTranslationOpacityPercent);
        assertFalse(merged.verticalFadeEnabled);
        assertEquals(777, merged.verticalFadeLengthTenthsDp);
        assertTrue(merged.inactiveRowFadeEnabled);
        assertEquals(76, merged.inactiveRowFadePercent);
        assertEquals("#123456", merged.primaryColor);
        assertFalse(merged.blurEnabled);
        assertFalse(merged.defaultTranslationEnabled);
    }
}
