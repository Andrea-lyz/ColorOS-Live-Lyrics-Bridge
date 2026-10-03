package io.github.andrealtb.artwork.am;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;
import java.net.SocketTimeoutException;

public class AmNetworkRetryTest {
    @Test public void timeoutReasonFollowsThePhaseThatStalled() {
        var timeout = new SocketTimeoutException("read timed out");
        assertEquals("network_connect_timeout", AmNetwork.transportReason("connect", timeout));
        assertEquals("network_headers_timeout", AmNetwork.transportReason("headers", timeout));
        assertEquals("network_read_timeout", AmNetwork.transportReason("body", timeout));
    }
    @Test public void resetAndTruncatedBodyStayGenericNetworkIo() {
        assertEquals("network_io", AmNetwork.transportReason("body", new IOException("Connection reset")));
        assertEquals("network_io", AmNetwork.transportReason("connect", new java.io.EOFException()));
    }
    @Test public void retryReasonsRemainCacheableTransportCodes() {
        for (String reason : new String[] { "network_io", "network_connect_timeout", "network_headers_timeout", "network_read_timeout" }) {
            assertTrue(reason, AmConnectivity.transport(reason));
        }
        assertFalse(AmConnectivity.transport("upstream_rate_limit"));
        assertFalse(AmConnectivity.transport("upstream_access_denied"));
    }
    @Test public void slowPagesFailFastWhileTheVideoGetsLongerGrace() {
        // The album page is the stage that once trickled for 18s; a short budget lets the retry win.
        assertTrue(AmNetwork.stageBudgetMs("web_album") < 10_000);
        assertEquals(6_000, AmNetwork.stageBudgetMs("catalog"));
        assertEquals(6_000, AmNetwork.stageBudgetMs("playlist"));
        assertTrue(AmNetwork.stageBudgetMs("video_file") > AmNetwork.stageBudgetMs("web_album"));
        // Bounded so a trickling rendition is abandoned quickly in favour of the next variant.
        assertTrue(AmNetwork.stageBudgetMs("video_file") <= 20_000);
    }
    @Test public void stageTimeoutIsARetryableTransportReason() {
        assertTrue(AmConnectivity.transport("network_stage_timeout"));
    }
}
