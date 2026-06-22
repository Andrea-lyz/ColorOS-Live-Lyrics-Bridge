package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LyricInfoTrackMatcherTest {
    @Test
    public void rawTrackHintMismatchMakesPayloadStale() throws Exception {
        LyricInfoContract.Payload payload = payload(
                "Instrumental Gap",
                "Artist B",
                "",
                "[ti:Previous Song]\n[ar:Artist A]\n[00:00]old line");

        assertFalse(LyricInfoTrackMatcher.payloadMatchesTrack(
                payload,
                "Instrumental Gap",
                "Artist B"));
    }

    @Test
    public void currentRawTrackHintIsStrongSaltFallbackEvidence() throws Exception {
        LyricInfoContract.Payload payload = payload(
                "Next Song",
                "Artist B",
                "",
                "[ti:Next Song]\n[ar:Artist B]\n[00:00]fresh line");

        assertTrue(LyricInfoTrackMatcher.hasStrongTrackEvidence(
                payload,
                "Next Song",
                "Artist B"));
        assertFalse(LyricInfoTrackMatcher.shouldClearSaltPlayerFallbackLyricInfo(
                payload,
                "Next Song",
                "Artist B",
                true,
                false,
                false,
                false));
    }

    @Test
    public void saltFallbackWithoutStrongEvidenceIsUnsafeOnTrackChange() throws Exception {
        LyricInfoContract.Payload payload = payload(
                "Instrumental Gap",
                "Artist B",
                "com.salt.music",
                "[00:00]old retained line");

        assertTrue(LyricInfoTrackMatcher.shouldClearSaltPlayerFallbackLyricInfo(
                payload,
                "Instrumental Gap",
                "Artist B",
                true,
                false,
                false,
                false));
    }

    @Test
    public void capturedCurrentLyricPreventsClearingSaltFallback() throws Exception {
        LyricInfoContract.Payload payload = payload(
                "Instrumental Gap",
                "Artist B",
                "com.salt.music",
                "[00:00]line");

        assertFalse(LyricInfoTrackMatcher.shouldClearSaltPlayerFallbackLyricInfo(
                payload,
                "Instrumental Gap",
                "Artist B",
                true,
                false,
                false,
                true));
    }

    private static LyricInfoContract.Payload payload(
            String title,
            String artist,
            String provider,
            String rawLyric) {
        return new LyricInfoContract.Payload(
                title,
                artist,
                "",
                rawLyric,
                rawLyric,
                "",
                provider,
                "",
                0L);
    }
}
