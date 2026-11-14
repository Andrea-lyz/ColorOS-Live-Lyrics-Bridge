package io.github.andrealtb.lockscreenlyrics.management.catalog

/**
 * DEBUG SOURCE SET ONLY. The TEST-ONLY catalog key (its private half is the published fixture
 * key in the Providers repository), so a device can exercise download → verify → install from
 * a locally signed catalog without publishing anything. Used only when the debug build was made
 * with `-PcllStoreTestCatalog=true` (BuildConfig.STORE_TEST_TRUST); the release source set
 * defines this object with no keys, and StoreTestTrustTest checks that.
 */
internal object StoreTestTrust {
    val keys: Map<String, String> = mapOf(
        "test-only-2026" to
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAErXDNAqErKJm7sdgBISfETC/Cu7oYZ30c2Y+nH2TetgdvQvM2aZgEX31O03EkoWAcmTJfvFG2Ll58VvSQCghiYQ==",
    )
}
