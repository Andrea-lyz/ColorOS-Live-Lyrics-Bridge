package io.github.andrealtb.lockscreenlyrics.management.catalog

import java.io.File
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** The test catalog key may only ever exist in the debug source set (plan §14.8). */
class StoreTestTrustTest {
    private fun source(path: String): File? =
        listOf(File(path), File("..", path)).firstOrNull { it.isFile }

    @Test fun releaseSourceSetTrustsNoTestKey() {
        val release = source("app/src/release/kotlin/io/github/andrealtb/lockscreenlyrics/management/catalog/StoreTestTrust.kt")
        assumeTrue("release source set visible", release != null)
        val text = release!!.readText()
        assertTrue(text.contains("emptyMap()"))
        assertFalse(text.contains("MFkw"))
    }

    @Test fun debugTestKeyIsTheFixtureKeyAndNeverAProductionKey() {
        // Unit tests compile the debug source set.
        val pem = javaClass.classLoader!!.getResource("catalog/TEST-ONLY-catalog-signing-key.pub.pem")!!.readText()
        val spki = pem.lines().filter { it.isNotBlank() && !it.startsWith("-----") }.joinToString("")
        assertEquals(mapOf("test-only-2026" to spki), StoreTestTrust.keys)
        assertTrue(StoreTestTrust.keys.keys.none { it in CatalogTrust.PRODUCTION_CATALOG_KEYS })
        assertTrue(StoreTestTrust.keys.values.none { it in CatalogTrust.PRODUCTION_CATALOG_KEYS.values })
        Base64.getDecoder().decode(spki)
        // Production trust alone still rejects anything signed with it.
        assertFalse("test-only-2026" in CatalogTrust.production().catalogKeys)
    }
}
