package io.github.andrealtb.artwork.contract;

import org.junit.Test;

import static org.junit.Assert.*;

public class ArtworkContractTest {
    private ArtworkQuery query(String url, long bytes) {
        return new ArtworkQuery("Song (Live)", "Artist", "Album", 240_001,
                url, 256, 256, 768, 768, bytes);
    }

    @Test public void keepsDurationInMillisecondsAndVersionMarkers() {
        ArtworkQuery query = query("https://music.apple.com/cn/album/album-name/123?i=456",
                ArtworkContract.MAX_FILE_BYTES);
        assertEquals(240_001, query.durationMs);
        assertEquals("Song (Live)", query.title);
        assertTrue(query.appleMusicUrl.endsWith("?i=456"));
    }

    @Test public void rejectsWrongSourceAndCredentialUrls() {
        String[] rejected = {
            "http://music.apple.com/cn/song/song/123", "https://music.apple.com.evil/cn/song/song/123",
            "https://evil@music.apple.com/cn/song/song/123", "https://music.apple.com:443/cn/song/song/123",
            "https://music.apple.com/cn/song/song/123#token", "123456", "https://music.apple.com/"
        };
        for (String url : rejected) {
            assertThrows(url, IllegalArgumentException.class, () -> query(url, 100));
        }
    }

    @Test public void rejectsUnboundedQueriesAndDoesNotClampSilently() {
        assertThrows(IllegalArgumentException.class, () -> query("", ArtworkContract.MAX_FILE_BYTES + 1));
        assertThrows(IllegalArgumentException.class, () -> new ArtworkQuery("x".repeat(513), "", "", 0,
                "", 1, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ArtworkQuery("", "", "", 0,
                "", 1, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ArtworkQuery("Song", "", "", -1,
                "", 1, 1, 1, 1, 1));
    }

    @Test public void validatesSquareActualDimensionsAndConsumerLimits() {
        ArtworkAsset asset = new ArtworkAsset("opaque-id", "v1", "video/avc", 768, 768, 1000, 1024, 60_000);
        assertTrue(asset.fits(query("", 2048)));
        assertFalse(asset.fits(query("", 1000)));
        ArtworkAsset larger = new ArtworkAsset("opaque-id", "v1", "video/hevc", 1080, 1080, 1000, 1024, 60_000);
        assertFalse(larger.fits(query("", 2048)));
        assertThrows(IllegalArgumentException.class, () -> new ArtworkAsset("id", "v1", "video/avc",
                1080, 720, 1000, 1024, 60_000));
        assertThrows(IllegalArgumentException.class, () -> new ArtworkAsset("id", "v1", "video/avc",
                2160, 2160, 1000, 1024, 60_000));
        assertThrows(IllegalArgumentException.class, () -> ArtworkContract.opaqueId("../../file"));
    }

    @Test public void separatesAmbiguityAndRetryFromPermanentNoMotion() {
        ArtworkResult ambiguous = ArtworkResult.failure(ArtworkResult.Status.AMBIGUOUS, "version_ambiguous");
        assertEquals(ArtworkResult.Status.AMBIGUOUS, ambiguous.status);
        ArtworkResult retry = new ArtworkResult(ArtworkResult.Status.RETRY_LATER, null, 1000, "rate_limited");
        assertEquals(1000, retry.retryAfterMs);
        assertThrows(IllegalArgumentException.class, () -> new ArtworkResult(ArtworkResult.Status.NO_MOTION,
                null, 1000, "rate_limited"));
        assertThrows(IllegalArgumentException.class, () -> ArtworkResult.failure(ArtworkResult.Status.READY, ""));
        assertThrows(IllegalArgumentException.class, () -> ArtworkResult.failure(ArtworkResult.Status.ERROR,
                "https://secret.example/token"));
    }

    @Test public void requiresMajorAndMediaCapabilitiesNotMatchingVersionNumbers() {
        assertTrue(ArtworkContract.supports(1, "video/mp4", "square"));
        assertFalse(ArtworkContract.supports(2, "video/mp4", "square"));
        assertFalse(ArtworkContract.supports(1, "application/vnd.apple.mpegurl", "square"));
        assertFalse(ArtworkContract.supports(1, "video/mp4", "tall"));
    }
}
