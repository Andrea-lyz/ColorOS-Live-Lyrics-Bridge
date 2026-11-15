package io.github.andrealtb.lockscreenlyrics.management

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ScopeRequestContractTest {
    private val id = "abcdefghijklmnopqrstuvwx"
    private val cone = "ink.trantor.coneplayer"
    private val coneGp = "ink.trantor.coneplayer.gp"

    private fun reply(protocol: Int = 1, replyId: String? = id, status: String?, granted: List<String>? = null) =
        parseScopeReply(protocol, replyId, status, granted, id, setOf(cone, coneGp))

    @Test fun trustedRepliesAreRead() {
        assertEquals(ScopeReply(ScopeReplyStatus.GRANTED, setOf(cone, coneGp)), reply(status = "GRANTED", granted = listOf(cone, coneGp)))
        assertEquals(ScopeReply(ScopeReplyStatus.PARTIAL, setOf(cone)), reply(status = "PARTIAL", granted = listOf(cone)))
        assertEquals(ScopeReplyStatus.NOT_GRANTED, reply(status = "NOT_GRANTED").status)
        // An invalid request is refused before the Provider accepts the id.
        assertEquals(ScopeReplyStatus.INVALID_REQUEST, reply(replyId = null, status = "INVALID_REQUEST").status)
    }

    @Test fun untrustedRepliesAreUnknown() {
        assertEquals(ScopeReplyStatus.UNKNOWN, reply(status = null).status) // no reply (process gone)
        assertEquals(ScopeReplyStatus.UNKNOWN, reply(protocol = 2, status = "GRANTED").status)
        assertEquals(ScopeReplyStatus.UNKNOWN, reply(replyId = "another-request-id-0000", status = "GRANTED").status)
        assertEquals(ScopeReplyStatus.UNKNOWN, reply(status = "GRANTED", granted = listOf("com.other.player")).status)
        assertEquals(ScopeReplyStatus.UNKNOWN, reply(status = "UNKNOWN").status)
        assertEquals(ScopeReplyStatus.UNKNOWN, reply(status = "SOMETHING_NEW").status)
    }

    @Test fun onlyInstalledDedicatedProvidersMissingScopeOfferTheRequest() {
        val base = StoreItem(
            id = "player-cone", name = "Cone", applicationId = "io.github.andrealtb.coloroslyrics.provider.cone",
            kind = ProviderKind.DEDICATED, entry = ProviderEntry.DEBUG_PAGE, installedVersion = "1.0",
            installState = InstallState.INSTALLED, hostDetected = true,
            enablement = Enablement.ENABLED_NO_SCOPE, installedHosts = listOf(cone),
        )
        assertTrue(base.offersScopeRequest())
        assertFalse(base.copy(enablement = Enablement.ENABLED).offersScopeRequest())
        assertFalse(base.copy(enablement = Enablement.NOT_ENABLED).offersScopeRequest())
        assertFalse(base.copy(installedHosts = emptyList()).offersScopeRequest())
        assertTrue(base.copy(entry = ProviderEntry.INFO_PAGE).offersScopeRequest()) // Readify
        assertFalse(base.copy(kind = ProviderKind.UNIVERSAL, entry = ProviderEntry.LAUNCHER).offersScopeRequest())
        val ids = (1..50).map { ScopeRequestContract.newRequestId() }
        assertTrue(ids.all { Regex("[a-z0-9]{24}").matches(it) })
        assertEquals(ids.size, ids.toSet().size)
    }

    /** The two repositories share no code; when both checkouts sit side by side, the strings must agree. */
    @Test fun constantsMatchTheProviderProtocol() {
        val rel = "provider-scope-request/src/main/kotlin/io/github/andrealtb/" +
            "coloroslyrics/provider/scoperequest/ScopeRequestProtocol.kt"
        // The build passes the Providers matrix path (scripts/dev.cmd, CLL_PROVIDER_MATRIX).
        val providersRoot = System.getenv("CLL_PROVIDER_MATRIX")?.let { File(it).parentFile?.parentFile }
        val file = listOfNotNull(
            providersRoot?.let { File(it, rel) },
            File("../../ColorOS-Live-Lyrics-Providers/$rel"),
            File("../ColorOS-Live-Lyrics-Providers/$rel"),
        ).firstOrNull { it.isFile }
        assumeTrue("Providers checkout visible", file != null)
        val source = file!!.readText()
        fun value(name: String) = Regex("const val $name = \"([^\"]+)\"").find(source)?.groupValues?.get(1)
        assertEquals(ScopeRequestContract.ACTION, value("ACTION"))
        assertEquals(ScopeRequestContract.EXTRA_PROTOCOL, value("EXTRA_PROTOCOL"))
        assertEquals(ScopeRequestContract.EXTRA_REQUEST_ID, value("EXTRA_REQUEST_ID"))
        assertEquals(ScopeRequestContract.EXTRA_HOSTS, value("EXTRA_HOSTS"))
        assertEquals(ScopeRequestContract.RESULT_STATUS, value("RESULT_STATUS"))
        assertEquals(ScopeRequestContract.RESULT_REQUESTED, value("RESULT_REQUESTED"))
        assertEquals(ScopeRequestContract.RESULT_GRANTED, value("RESULT_GRANTED"))
        assertTrue(source.contains("const val VERSION = ${ScopeRequestContract.VERSION}"))
        val providerStatuses = Regex("enum class ScopeRequestStatus \\{([^}]*)\\}").find(source)!!.groupValues[1]
            .lines().map { it.trim().removeSuffix(",") }.filter { it.matches(Regex("[A-Z_]+")) }
        assertEquals(providerStatuses, ScopeReplyStatus.entries.map { it.name } - "UNKNOWN")
    }
}
