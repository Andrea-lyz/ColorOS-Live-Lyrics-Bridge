package io.github.andrealtb.artwork.am;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.nio.file.Files;
import java.io.File;
import io.github.andrealtb.artwork.contract.ArtworkAsset;
import io.github.andrealtb.artwork.contract.ArtworkResult;
import io.github.andrealtb.artwork.contract.ArtworkResult.Status;

public class AmCacheTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private File video(AmCache cache, int resolution) throws Exception {
        File temp = cache.temporary(); Files.write(temp.toPath(), ("fixture-" + resolution).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        File file = cache.commit(temp);
        var asset = new ArtworkAsset("asset", "version", "video/avc", resolution, resolution, 15000, file.length(), 60000);
        cache.rememberVideo("us", "album", file, asset); cache.unpin(file); return file;
    }
    @Test public void cacheLimitTrimsLeastRecentlyUsedVideosAndKeepsPinnedOnes() throws Exception {
        var cache = new AmCache(temporary.newFolder());
        File oldest = video(cache, 360), newest = video(cache, 408);
        oldest.setLastModified(1_000_000L); newest.setLastModified(2_000_000L);
        cache.setBudget(oldest.length()); cache.cleanup();
        assertFalse("the least recently used video goes first", oldest.exists());
        assertTrue(newest.exists());
        cache.unpin(newest);
        cache.setBudget(1); cache.cleanup();
        assertFalse("a lowered limit trims the cache right away", newest.exists());
        assertTrue(cache.budgetBytes() > 0);
        cache.setBudget(0); assertEquals("a non-positive budget never means evict everything", 1, cache.budgetBytes());
    }
    @Test public void immutableContentAndPinsSurviveClearUntilLastLeaseReleased() throws Exception {
        var cache = new AmCache(temporary.newFolder());
        var temp = cache.temporary(); Files.write(temp.toPath(), new byte[] {1, 2, 3});
        var file = cache.commit(temp);
        String key = AmCache.key(AmIdentityTest.query("Style", "1989", 288), "us");
        cache.remember(key, file, null);
        var hit = cache.lookup(key); assertEquals(file, hit.file());
        cache.clear(); assertTrue(file.exists());
        cache.unpin(file); cache.clear(); assertTrue(file.exists());
        cache.unpin(hit.file()); cache.clear(); assertFalse(file.exists());
    }
    @Test public void temporaryNetworkFailureStaysRetryableAndCancellationIsNotCached() throws Exception {
        var cache = new AmCache(temporary.newFolder());
        String key = AmCache.key(AmIdentityTest.query("Style", "1989", 288), "us");
        cache.remember(key, null, new ArtworkResult(Status.RETRY_LATER, null, 60000, "network_io"));
        var hit = cache.lookup(key); assertEquals(Status.RETRY_LATER, hit.failure().status);
        assertTrue(hit.failure().retryAfterMs > 0);
        cache.clear();
        cache.remember(key, null, ArtworkResult.failure(Status.ERROR, "cancelled")); assertNull(cache.lookup(key));
    }
    @Test public void cachedQueryDoesNotReferenceHalfDownloadedFile() throws Exception {
        var cache = new AmCache(temporary.newFolder());
        var temp = cache.temporary(); Files.write(temp.toPath(), new byte[] {1, 2, 3});
        String key = AmCache.key(AmIdentityTest.query("Style", "1989", 288), "us");
        assertNull(cache.lookup(key)); assertTrue(temp.getName().endsWith(".part"));
    }
    @Test public void albumSnapshotIsMarketAndEditionScopedAndSurvivesProcessReopen() throws Exception {
        var root = temporary.newFolder(); var cache = new AmCache(root);
        var query = AmIdentityTest.query("Style", "1989", 288);
        cache.rememberAlbum(query, "us", AmPage.album(AmPageTest.page("null"), "10"));
        assertNotNull(new AmCache(root).album(query, "us"));
        assertNull(cache.album(query, "cn"));
        assertNull(cache.album(AmIdentityTest.query("Style", "1989 Deluxe", 288), "us"));
    }
    @Test public void albumVideoKeySharesAcrossSongsButSeparatesAlbumIdAndDisplaySize() {
        var first = AmIdentityTest.query("Style", "1989", 288);
        var next = AmIdentityTest.query("Blank Space", "1989", 288);
        assertEquals(AmCache.albumAssetKey(first, "us", "10"), AmCache.albumAssetKey(next, "us", "10"));
        assertNotEquals(AmCache.albumAssetKey(first, "us", "10"), AmCache.albumAssetKey(first, "us", "20"));
        assertNotEquals(AmCache.albumAssetKey(first, "us", "10"), AmCache.albumAssetKey(AmIdentityTest.query("Style", "1989", 1080), "us", "10"));
    }
}
