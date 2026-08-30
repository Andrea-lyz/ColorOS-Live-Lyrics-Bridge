package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class LyricUiSettingsPolicyTest {
    @Test
    public void timeoutTextUsesCanonicalReadbackRange() {
        assertEquals(0, LyricUiSettings.parseScreenTimeoutSeconds(""));
        assertEquals(1, LyricUiSettings.parseScreenTimeoutSeconds("1"));
        assertEquals(86_400, LyricUiSettings.parseScreenTimeoutSeconds("999999"));
        assertEquals(86_400,
                LyricUiSettings.parseScreenTimeoutSeconds("999999999999999999999999"));
        assertEquals(0, LyricUiSettings.parseScreenTimeoutSeconds("invalid"));
    }

    @Test
    public void translationProgressDependencyHintOnlyShowsForLineTimedGap() {
        assertEquals(true,
                LyricUiSettings.shouldShowTranslationProgressDependencyHint(false, true));
        assertEquals(false,
                LyricUiSettings.shouldShowTranslationProgressDependencyHint(true, true));
        assertEquals(false,
                LyricUiSettings.shouldShowTranslationProgressDependencyHint(false, false));
    }
}
