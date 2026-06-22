package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LyricTextMatchPolicyTest {
    @Test
    public void shortRepeatedPrefixDoesNotMatchLongerLyric() {
        assertFalse(LyricTextMatchPolicy.hasSubstantialPrefix(
                "No, no",
                "No, no body, no crime"));
    }

    @Test
    public void substantialTruncatedLyricPrefixStillMatches() {
        assertTrue(LyricTextMatchPolicy.hasSubstantialPrefix(
                "I think he did it, but I just can't",
                "I think he did it, but I just can't prove it"));
    }
}
