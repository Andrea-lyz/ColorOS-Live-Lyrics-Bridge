package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import org.junit.Test;
import static org.junit.Assert.*;

public class ArtworkLiveQueryTest {
    @Test public void acceptedMetadataGoesToProviderWithoutSessionOrMediaIdentity() {
        var metadata = new ArtworkTrackIdentityPolicy.Metadata("private-host-id", "Style (Taylor's Version)", "Taylor Swift", "1989 [Deluxe]", 231000);
        var query = ArtworkLiveQuery.from(metadata, 1312, 1312);
        assertEquals(metadata.title(), query.title); assertEquals(metadata.album(), query.album);
        assertEquals(231000, query.durationMs); assertEquals("", query.appleMusicUrl);
        assertEquals(1080, query.displayWidthPx); assertEquals(1080, query.maxWidth);
    }
    @Test(expected = IllegalArgumentException.class) public void incompleteIdentityRemainsStatic() {
        ArtworkLiveQuery.from(new ArtworkTrackIdentityPolicy.Metadata("id", "Song", "", "", 0), 288, 288);
    }
}
