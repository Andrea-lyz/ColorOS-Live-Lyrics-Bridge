package io.github.andrealtb.lockscreenlyrics.management

import android.content.Intent
import android.os.Bundle
import java.security.SecureRandom

/**
 * Bridge side of management protocol v1 (plan §7.2–§7.3, §14.13–§14.14): asks an installed dedicated
 * Provider to request ITS OWN LSPosed scope. The Bridge only names the players installed on this
 * phone; the Provider checks them against its compiled scope list and the framework shows its own
 * prompt. The strings mirror Providers `ScopeRequestProtocol` (the repositories share no code;
 * `ScopeRequestContractTest` compares them when both checkouts are present). Providers that predate
 * the protocol have no matching Activity; the store then says to update or use LSPosed.
 */
internal object ScopeRequestContract {
    const val VERSION = 1
    const val ACTION = "io.github.andrealtb.coloroslyrics.action.REQUEST_PROVIDER_SCOPE"
    const val EXTRA_PROTOCOL = "cll.scope.protocol"
    const val EXTRA_REQUEST_ID = "cll.scope.requestId"
    const val EXTRA_HOSTS = "cll.scope.hosts"
    const val RESULT_STATUS = "cll.scope.status"
    const val RESULT_REQUESTED = "cll.scope.requested"
    const val RESULT_GRANTED = "cll.scope.granted"

    private val random = SecureRandom()

    fun newRequestId(): String = buildString {
        repeat(24) { append("0123456789abcdefghijklmnopqrstuvwxyz"[random.nextInt(36)]) }
    }

    fun intent(applicationId: String, hosts: List<String>, requestId: String): Intent =
        Intent(ACTION).setPackage(applicationId)
            .putExtra(EXTRA_PROTOCOL, VERSION)
            .putExtra(EXTRA_REQUEST_ID, requestId)
            .putExtra(EXTRA_HOSTS, hosts.toTypedArray())
}

/** Mirrors Providers `ScopeRequestStatus`; UNKNOWN covers anything else, including no reply. */
internal enum class ScopeReplyStatus {
    ALREADY_GRANTED, GRANTED, PARTIAL, NOT_GRANTED, PENDING, NO_SERVICE, FAILED, INVALID_REQUEST, UNKNOWN,
}

internal data class ScopeReply(val status: ScopeReplyStatus, val granted: Set<String> = emptySet())

/**
 * Reads the Provider's reply. A reply for another request id, a different protocol, or hosts the
 * Bridge never asked for is not trusted: the card then re-reads the LSPosed state instead.
 */
internal fun parseScopeReply(extras: Bundle?, requestId: String, asked: Set<String>): ScopeReply =
    parseScopeReply(
        protocol = extras?.getInt(ScopeRequestContract.EXTRA_PROTOCOL, 0) ?: 0,
        replyId = extras?.getString(ScopeRequestContract.EXTRA_REQUEST_ID),
        status = extras?.getString(ScopeRequestContract.RESULT_STATUS),
        granted = extras?.getStringArray(ScopeRequestContract.RESULT_GRANTED)?.toList(),
        requestId = requestId,
        asked = asked,
    )

internal fun parseScopeReply(
    protocol: Int, replyId: String?, status: String?, granted: List<String>?, requestId: String, asked: Set<String>,
): ScopeReply {
    if (protocol != ScopeRequestContract.VERSION) return ScopeReply(ScopeReplyStatus.UNKNOWN)
    val parsed = ScopeReplyStatus.entries.firstOrNull { it.name == status && it != ScopeReplyStatus.UNKNOWN }
        ?: return ScopeReply(ScopeReplyStatus.UNKNOWN)
    // An invalid request is answered before the id is accepted, so it carries none.
    if (parsed == ScopeReplyStatus.INVALID_REQUEST) return ScopeReply(parsed)
    if (replyId != requestId) return ScopeReply(ScopeReplyStatus.UNKNOWN)
    val hosts = granted.orEmpty().toSet()
    if (!asked.containsAll(hosts)) return ScopeReply(ScopeReplyStatus.UNKNOWN)
    return ScopeReply(parsed, hosts)
}

/** Every dedicated Provider (the 12 on the shared settings library and Readify) answers the protocol. */
internal fun StoreItem.offersScopeRequest(): Boolean =
    kind == ProviderKind.DEDICATED && installedVersion != null &&
        enablement == Enablement.ENABLED_NO_SCOPE && installedHosts.isNotEmpty()
