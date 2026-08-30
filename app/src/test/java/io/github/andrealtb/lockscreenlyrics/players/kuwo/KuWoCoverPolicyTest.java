package io.github.andrealtb.lockscreenlyrics.players.kuwo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class KuWoCoverPolicyTest {
    @Test
    public void snapshotKeyPrefersNonEmptyMediaId() {
        assertEquals("id:kuwo-1", KuWoCoverPolicy.artworkSnapshotKey("kuwo-1", "song|artist"));
        assertEquals("song|artist", KuWoCoverPolicy.artworkSnapshotKey("", "song|artist"));
        assertEquals("song|artist", KuWoCoverPolicy.artworkSnapshotKey(null, "song|artist"));
        assertEquals("", KuWoCoverPolicy.artworkSnapshotKey(null, null));
    }

    @Test
    public void httpImgKuWoHostIsEligibleForHttpsUpgrade() {
        assertTrue(KuWoCoverPolicy.isKuWoHttpCoverHost("http", "img1.kuwo.cn"));
        assertTrue(KuWoCoverPolicy.isKuWoHttpCoverHost("http", "img2.kuwo.cn"));
        assertFalse(KuWoCoverPolicy.isKuWoHttpCoverHost("https", "img1.kuwo.cn"));
        assertFalse(KuWoCoverPolicy.isKuWoHttpCoverHost("http", "cdn.kuwo.cn"));
        assertFalse(KuWoCoverPolicy.isKuWoHttpCoverHost("http", "img1.example.com"));
        assertFalse(KuWoCoverPolicy.isKuWoHttpCoverHost("http", ""));
        assertFalse(KuWoCoverPolicy.isKuWoHttpCoverHost("http", null));
    }

    @Test
    public void plausibleCoverSizeRequiresMinEdge() {
        assertTrue(KuWoCoverPolicy.isPlausibleCoverSize(96, 96));
        assertTrue(KuWoCoverPolicy.isPlausibleCoverSize(300, 96));
        assertFalse(KuWoCoverPolicy.isPlausibleCoverSize(95, 96));
        assertFalse(KuWoCoverPolicy.isPlausibleCoverSize(96, 95));
    }

    @Test
    public void seedlingRepairRequiresImplausibleIncomingSameTrackAndSnapshot() {
        assertFalse(KuWoCoverPolicy.shouldRepairSeedlingArtwork(
                true, "song|artist", "song|artist", true));
        assertFalse(KuWoCoverPolicy.shouldRepairSeedlingArtwork(
                false, "", "song|artist", true));
        assertFalse(KuWoCoverPolicy.shouldRepairSeedlingArtwork(
                false, "song|artist", "other|track", true));
        assertFalse(KuWoCoverPolicy.shouldRepairSeedlingArtwork(
                false, "song|artist", "song|artist", false));
        assertTrue(KuWoCoverPolicy.shouldRepairSeedlingArtwork(
                false, "song|artist", "song|artist", true));
        assertTrue(KuWoCoverPolicy.shouldRepairSeedlingArtwork(
                false, "song|artist", "", true));
        assertTrue(KuWoCoverPolicy.shouldRepairSeedlingArtwork(
                false, "song|artist", null, true));
    }

    @Test
    public void missingIconBitmapIsNotPlausible() {
        assertFalse(KuWoCoverPolicy.isPlausibleCoverIconBitmap(null));
        assertFalse(KuWoCoverPolicy.isPlausibleCoverBitmap(null));
    }

    @Test
    public void uniformColorSamplesAreRejectedAndVariedSamplesAreKept() {
        int red = 0xFFFF0000;
        int blue = 0xFF0000FF;
        assertTrue(KuWoCoverPolicy.isHighConfidenceUniformColor(
                128,
                128,
                KuWoCoverPolicy.UNIFORM_SAMPLE_STRIDE,
                (x, y) -> red));
        assertFalse(KuWoCoverPolicy.isHighConfidenceUniformColor(
                128,
                128,
                KuWoCoverPolicy.UNIFORM_SAMPLE_STRIDE,
                (x, y) -> x < 64 ? red : blue));
    }
}
