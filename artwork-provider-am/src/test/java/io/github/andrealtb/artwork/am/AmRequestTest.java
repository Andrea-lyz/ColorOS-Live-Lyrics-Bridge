package io.github.andrealtb.artwork.am;

import org.junit.Test;
import static org.junit.Assert.*;
import io.github.andrealtb.artwork.contract.ArtworkResult.Status;

public class AmRequestTest {
    @Test public void leaseOwnerExpiryAndNonReusableRequestId() {
        var leases = new RequestLeaseTable<String>();
        assertTrue(leases.add(100, "id", "owner", 0, 60000));
        assertFalse(leases.add(100, "id", "replacement", 1, 60000));
        assertNull(leases.get(200, "id", 1)); assertNull(leases.remove(200, "id"));
        assertEquals("owner", leases.get(100, "id", 59999)); assertNull(leases.get(100, "id", 60000));
        assertEquals(1, leases.reap(60000).size());
    }
    @Test public void cancellationIsObservedBeforeAnyNextNetworkStep() throws Exception {
        var task = new AmNetwork.Task(); task.cancel();
        try { task.check(); fail(); } catch (AmFailure expected) { assertEquals(Status.ERROR, expected.status); assertEquals("cancelled", expected.reason); }
    }
    @Test public void queryCompletionAndPhysicalSizeCannotReuseWrongCacheEntry() {
        var incomplete = AmIdentityTest.query("Style", "", 288);
        var complete = AmIdentityTest.query("Style", "1989", 288);
        assertNotEquals(AmCache.key(incomplete, "us"), AmCache.key(complete, "us"));
        assertNotEquals(AmCache.key(complete, "us"), AmCache.key(complete, "cn"));
        assertNotEquals(AmCache.key(complete, "us"), AmCache.key(AmIdentityTest.query("Style", "1989", 1080), "us"));
    }
}
