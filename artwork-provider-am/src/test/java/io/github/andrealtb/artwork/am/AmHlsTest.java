package io.github.andrealtb.artwork.am;

import org.junit.Test;
import static org.junit.Assert.*;
import java.net.URI;

public class AmHlsTest {
    @Test public void realPublicMasterAndChildSelectAvc408AndWholeFile() throws Exception {
        String master, child;
        try (var stream = getClass().getResourceAsStream("/square-master.m3u8")) {
            master = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        try (var stream = getClass().getResourceAsStream("/child-avc1-408x408.m3u8")) {
            child = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        var variant = AmHls.variants(BASE, master, AmIdentityTest.query("Style", "1989", 288)).get(0);
        assertEquals(408, variant.width()); assertEquals("avc1.64001f", variant.codec());
        assertEquals(728484, AmHls.filePlan(variant.uri(), child, 20 * 1024 * 1024).bytes());
    }
    static final URI BASE = URI.create("https://mvod.itunes.apple.com/a/master.m3u8");
    static final String CHILD = "#EXTM3U\n#EXT-X-PLAYLIST-TYPE:VOD\n#EXT-X-MAP:URI=\"clip.mp4\",BYTERANGE=\"985@0\"\n"
            + "#EXTINF:5.54721,\n#EXT-X-BYTERANGE:134643@985\nclip.mp4\n"
            + "#EXTINF:5.58892,\n#EXT-X-BYTERANGE:141362@135628\nclip.mp4\n"
            + "#EXTINF:5.58892,\n#EXT-X-BYTERANGE:184531@276990\nclip.mp4\n#EXT-X-ENDLIST\n";
    @Test public void repeatedFileUrisAreOneWholeFileWithContinuousRanges() throws Exception {
        var plan = AmHls.filePlan(BASE, CHILD, 1_000_000);
        assertEquals(461521, plan.bytes()); assertEquals(16.72505, plan.durationSeconds(), 0.000001);
        assertEquals(URI.create("https://mvod.itunes.apple.com/a/clip.mp4"), plan.uri());
    }
    @Test public void rejectMissingRangeEncryptionGapsLiveAndMultipleFiles() throws Exception {
        for (String broken : new String[] { CHILD.replace("#EXT-X-ENDLIST", ""), CHILD.replace("#EXT-X-PLAYLIST-TYPE:VOD", ""),
                CHILD + "#EXT-X-KEY:METHOD=AES-128\n", CHILD.replace("134643@985", "134643@986"),
                CHILD.replace("#EXT-X-BYTERANGE:134643@985\n", ""), CHILD.replace("184531@276990\nclip.mp4", "184531@276990\nother.mp4") }) {
            try { AmHls.filePlan(BASE, broken, 1_000_000); fail(); } catch (AmFailure expected) { assertEquals("unsupported_hls_layout", expected.reason); }
        }
    }
    @Test public void oversizeWholeFileIsRejectedBeforeDownload() throws Exception {
        try { AmHls.filePlan(BASE, CHILD, 461520); fail(); } catch (AmFailure expected) { assertEquals("unsupported_hls_layout", expected.reason); }
    }
    @Test public void implicitOffsetsAreAcceptedOnlyWithSameFile() throws Exception {
        assertEquals(461521, AmHls.filePlan(BASE, CHILD.replace("141362@135628", "141362"), 1_000_000).bytes());
    }
    static String stream(int size, String codec, long bitrate, String file) {
        return "#EXT-X-STREAM-INF:CODECS=\"" + codec + "\",VIDEO-RANGE=SDR,FRAME-RATE=23.976,RESOLUTION=" + size + "x" + size
                + ",BANDWIDTH=" + bitrate + "\n" + file + "\n";
    }
    @Test public void cardPrefersNativeAvc408RatherThanHevc360OrTrickPlay() throws Exception {
        String master = "#EXTM3U\n#EXT-X-I-FRAME-STREAM-INF:RESOLUTION=1x1,URI=\"trick.m3u8\"\n"
                + stream(360, "hvc1.2.20000000.L123.B0", 220757, "360.m3u8")
                + stream(768, "avc1.64001f", 2117093, "768.m3u8") + stream(408, "avc1.64001f", 348452, "408.m3u8");
        var result = AmHls.variants(BASE, master, AmIdentityTest.query("Style", "1989", 288));
        assertEquals(2, result.size()); assertEquals(408, result.get(0).width());
    }
    @Test public void sameSizeBitratesRemainDistinctAndLowestComesFirst() throws Exception {
        String master = "#EXTM3U\n" + stream(768, "avc1.64001f", 2000000, "hi.m3u8") + stream(768, "avc1.64001f", 1000000, "low.m3u8");
        var result = AmHls.variants(BASE, master, AmIdentityTest.query("Style", "1989", 768));
        assertEquals(2, result.size()); assertEquals(1000000, result.get(0).bitrate());
    }
    @Test public void largeHostDoesNotChooseOverBudget2160() throws Exception {
        String master = "#EXTM3U\n" + stream(2160, "avc1.64001f", 9000000, "2160.m3u8")
                + stream(1080, "avc1.640020", 5000000, "1080.m3u8") + stream(768, "avc1.64001f", 2000000, "768.m3u8");
        var result = AmHls.variants(BASE, master, AmIdentityTest.query("Style", "1989", 1312));
        assertEquals(2, result.size()); assertEquals(1080, result.get(0).width());
    }
    @Test public void stalledSmallRenditionReachesAnotherSizeBeforeBitrateTwins() throws Exception {
        String master = "#EXTM3U\n" + stream(360, "avc1.64001f", 100000, "360-low.m3u8")
                + stream(360, "avc1.64001f", 200000, "360-high.m3u8")
                + stream(768, "avc1.64001f", 300000, "768.m3u8")
                + stream(1080, "avc1.640020", 400000, "1080.m3u8")
                + stream(2160, "avc1.640020", 500000, "2160.m3u8");
        var variants = AmHls.variants(BASE, master, AmIdentityTest.query("Style", "1989", 288));
        assertEquals(360, variants.get(0).width());
        var failed = variants.remove(0);
        AmHls.transportFallback(variants, failed);
        assertEquals(1080, variants.get(0).width());
        assertEquals(768, variants.get(1).width());
        assertEquals(360, variants.get(2).width());
        assertEquals(3, variants.size()); // Never add an over-budget size while changing fallback order.
    }
    @Test public void crossHostAndCleartextMediaAreRejected() {
        for (String uri : new String[] { "http://mvod.itunes.apple.com/a.mp4", "https://evil.test/a.mp4", "https://mvod.itunes.apple.com:443/a.mp4" }) {
            try { AmHls.mediaUri(URI.create(uri)); fail(); } catch (IllegalArgumentException expected) { /* allowlisted transport */ }
        }
    }
}
