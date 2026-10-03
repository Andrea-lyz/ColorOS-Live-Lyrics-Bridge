package io.github.andrealtb.artwork.am;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.io.File;
import java.nio.file.Files;
import io.github.andrealtb.artwork.contract.ArtworkAsset;
import io.github.andrealtb.artwork.contract.ArtworkQuery;
import io.github.andrealtb.artwork.contract.ArtworkResult;
import io.github.andrealtb.artwork.contract.ArtworkResult.Status;

public class AmRecoveryTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private File video(AmCache cache, int resolution) throws Exception {
        File temp = cache.temporary(); Files.write(temp.toPath(), ("fixture-" + resolution).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        File file = cache.commit(temp);
        var asset = new ArtworkAsset("asset", "version", "video/avc", resolution, resolution, 15000, file.length(), 60000);
        cache.rememberVideo("us", "album", file, asset); cache.unpin(file); return file;
    }
    @Test public void largeVideoCanServeSmallQueryBeforeItsPreferredVariantDownloads() throws Exception {
        var cache = new AmCache(temporary.newFolder()); var large = video(cache, 1080);
        var hit = cache.compatibleVideo(AmIdentityTest.query("Style", "1989", 288), "us", "album");
        assertEquals(large, hit.file()); cache.unpin(hit.file());
    }
    @Test public void lowestSufficientCachedVariantIsPreferredAndNeverCrossesAlbumOrMarket() throws Exception {
        var cache = new AmCache(temporary.newFolder()); video(cache, 1080); File small = video(cache, 360);
        var query = AmIdentityTest.query("Style", "1989", 288);
        var hit = cache.compatibleVideo(query, "us", "album"); assertEquals(small, hit.file()); cache.unpin(hit.file());
        assertNull(cache.compatibleVideo(query, "cn", "album")); assertNull(cache.compatibleVideo(query, "us", "other"));
    }
    @Test public void largeHostNeverReusesTheSmallCardVideoUnlessFetchingTheSharpOneFailed() throws Exception {
        // Device feedback: the large cover (1312 px) played the small card's 408 px video, visibly blurry.
        var cache = new AmCache(temporary.newFolder()); File small = video(cache, 408);
        var large = AmIdentityTest.query("Style", "1989", 1312);
        assertNull(cache.compatibleVideo(large, "us", "album", true));
        var fallback = cache.compatibleVideo(large, "us", "album", false);
        assertEquals(small, fallback.file()); cache.unpin(fallback.file());
    }
    @Test public void largestFetchableSizeCountsAsSharpEnoughForAnOversizedHost() throws Exception {
        var cache = new AmCache(temporary.newFolder()); File full = video(cache, 1080); video(cache, 408);
        var hit = cache.compatibleVideo(AmIdentityTest.query("Style", "1989", 1312), "us", "album", true);
        assertEquals("1080 is the resolution cap, so it is sufficient for a 1312 px host", full, hit.file());
        cache.unpin(hit.file());
    }
    @Test public void strictFileAndResolutionCapsAreRespectedByCacheFallback() throws Exception {
        var cache = new AmCache(temporary.newFolder()); video(cache, 1080);
        var q = AmIdentityTest.query("Style", "1989", 288);
        var limited = new ArtworkQuery(q.title, q.artist, q.album, q.durationMs, "", 288, 288, 360, 360, q.maxFileBytes);
        assertNull(cache.compatibleVideo(limited, "us", "album"));
        var tiny = new ArtworkQuery(q.title, q.artist, q.album, q.durationMs, "", 288, 288, 1080, 1080, 1);
        assertNull(cache.compatibleVideo(tiny, "us", "album"));
    }
    @Test public void fallbackPinSurvivesClearUntilConsumerReleasesIt() throws Exception {
        var cache = new AmCache(temporary.newFolder()); File large = video(cache, 1080);
        var hit = cache.compatibleVideo(AmIdentityTest.query("Style", "1989", 288), "us", "album");
        cache.clear(); assertTrue(large.exists()); cache.unpin(hit.file()); cache.clear(); assertFalse(large.exists());
    }
    @Test public void networkRecoveryDiscardsTransportBackoffButNotRateLimit() throws Exception {
        var cache = new AmCache(temporary.newFolder()); String key = AmCache.key(AmIdentityTest.query("Style", "1989", 288), "us");
        cache.remember(key, null, new ArtworkResult(Status.RETRY_LATER, null, 30000, "network_io"), "old");
        assertNotNull(cache.lookup(key, "old")); assertNull(cache.lookup(key, "restored"));
        cache.remember(key, null, new ArtworkResult(Status.RETRY_LATER, null, 30000, "upstream_rate_limit"), "old");
        assertEquals("upstream_rate_limit", cache.lookup(key, "restored").failure().reason);
    }
    @Test public void stableValidationDoesNotInvalidateCurrentTransportBackoff() {
        var epoch = new AmConnectivity.Epoch(); String initial = epoch.update("wifi", true);
        assertEquals(initial, epoch.update("wifi", true)); epoch.update(null, false);
        String restored = epoch.update("wifi", true); assertNotEquals(initial, restored);
        assertEquals(restored, epoch.update("wifi", true)); assertNotEquals(restored, epoch.update("cell", true));
    }
    @Test public void legacyTransportFailureWithoutEpochCanRetryAfterUpgrade() throws Exception {
        var cache = new AmCache(temporary.newFolder()); String key = AmCache.key(AmIdentityTest.query("Style", "1989", 288), "us");
        cache.remember(key, null, new ArtworkResult(Status.RETRY_LATER, null, 30000, "network_io"));
        assertNull(cache.lookup(key, "current"));
    }
}
