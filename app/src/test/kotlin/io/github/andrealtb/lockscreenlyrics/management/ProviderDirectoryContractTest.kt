package io.github.andrealtb.lockscreenlyrics.management

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The store directory is a mirror of the Providers machine matrix (plan §6.1: no second business
 * list). Skipped only when the sibling Providers checkout is not available to the test.
 */
class ProviderDirectoryContractTest {
    private fun locate(vararg candidates: String?): File? =
        candidates.filterNotNull().map(::File).firstOrNull { it.isFile }

    private fun matrix(): JSONObject {
        val file = locate(
            // Builds under a substituted ASCII drive cannot see the sibling checkout; pass it explicitly.
            System.getenv("CLL_PROVIDER_MATRIX"),
            "../../ColorOS-Live-Lyrics-Providers/release/v5-provider-matrix.json",
            "../ColorOS-Live-Lyrics-Providers/release/v5-provider-matrix.json",
            "../provider-source/release/v5-provider-matrix.json",
            "provider-source/release/v5-provider-matrix.json",
        )
        assumeTrue("Providers v5-provider-matrix.json is visible to unit tests", file != null)
        return JSONObject(file!!.readText())
    }

    private fun expected(): Map<String, Pair<String, Set<String>>> {
        val json = matrix()
        val result = linkedMapOf<String, Pair<String, Set<String>>>()
        val providers = json.getJSONArray("providers")
        for (i in 0 until providers.length()) {
            val p = providers.getJSONObject(i)
            val scopes = p.getJSONArray("scopes")
            val hosts = (0 until scopes.length()).map { scopes.getString(it) }.filter { it != "system" }.toSet()
            result[p.getString("module")] = p.getString("applicationId") to hosts
        }
        val artwork = json.getJSONArray("artworkProviders")
        for (i in 0 until artwork.length()) {
            val a = artwork.getJSONObject(i)
            result[a.getString("module")] = a.getString("applicationId") to emptySet()
        }
        return result
    }

    @Test fun directoryMatchesTheProviderMatrixExactly() {
        val actual = ProviderDirectory.entries.associate { it.module to (it.applicationId to it.hostPackages.toSet()) }
        assertEquals(expected(), actual)
    }

    @Test fun manifestQueriesCoverEveryPluginAndSupportedPlayer() {
        val manifest = locate("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
        assumeTrue("app manifest is visible to unit tests", manifest != null)
        val declared = Regex("<package android:name=\"([^\"]+)\"").findAll(manifest!!.readText())
            .map { it.groupValues[1] }.toSet()
        for (entry in ProviderDirectory.entries) {
            assertTrue("missing <queries> for ${entry.applicationId}", entry.applicationId in declared)
            for (host in entry.hostPackages) {
                assertTrue("missing <queries> for host $host", host in declared)
            }
        }
        assertTrue("store must not need QUERY_ALL_PACKAGES",
            !manifest.readText().contains("android.permission.QUERY_ALL_PACKAGES"))
    }
}
