package io.github.andrealtb.lockscreenlyrics.management.catalog

import io.github.andrealtb.lockscreenlyrics.management.net.HttpsTransport
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Where the signed stable catalog is published (plan §14.5.2, D1 = M): the `store-catalog`
 * branch of the Providers repository, through jsDelivr first and raw GitHub second. Mirrors only
 * carry bytes; trust comes from the envelope signature. Bound to the Providers
 * `release/catalog-trust.json` by CatalogVerifierTest.
 */
internal object CatalogSources {
    const val REPOSITORY = "Andrea-lyz/ColorOS-Live-Lyrics-Providers"
    const val BRANCH = "store-catalog"
    const val PATH = "catalog/stable.json"

    val urls: List<String> = listOf(
        "https://cdn.jsdelivr.net/gh/$REPOSITORY@$BRANCH/$PATH",
        "https://raw.githubusercontent.com/$REPOSITORY/$BRANCH/$PATH",
    )
    val hosts: Set<String> = setOf("cdn.jsdelivr.net", "raw.githubusercontent.com")
}

internal enum class RefreshOutcome {
    /** A newer catalog was verified and stored. */
    UPDATED,
    /** Mirrors served the catalog already held, or an older one (a stale CDN copy). */
    UP_TO_DATE,
    /** A catalog arrived but failed verification; see [RefreshResult.rejection]. */
    REJECTED,
    /** A valid newer catalog could not be stored, so it is not used. */
    PERSIST_FAILED,
    /** No mirror answered. */
    NETWORK_FAILED,
}

internal data class RefreshResult(val outcome: RefreshOutcome, val rejection: CatalogRejection? = null)

/**
 * Fetches every mirror and offers each response to [store]; the store keeps the highest valid
 * revision, so the order and a lagging mirror do not matter. The caller reloads the store
 * afterwards to get the state it should display.
 */
internal class CatalogRefresher(
    private val store: CatalogStore,
    private val transport: HttpsTransport,
    private val urls: List<String> = CatalogSources.urls,
    private val hosts: Set<String> = CatalogSources.hosts,
) {
    fun refresh(nowEpochSeconds: Long): RefreshResult {
        var best = RefreshOutcome.NETWORK_FAILED
        var rejection: CatalogRejection? = null
        for (url in urls) {
            val bytes = try {
                download(url)
            } catch (_: IOException) {
                continue
            } catch (_: RuntimeException) {
                // A misbehaving platform/network stack must not crash the store.
                continue
            } ?: run {
                // Larger than any valid envelope: let the verifier name it.
                rejection = rejection ?: CatalogRejection.TOO_LARGE
                best = maxOf(best, RefreshOutcome.REJECTED, PRIORITY)
                continue
            }
            val outcome = when (val offer = store.offer(bytes, nowEpochSeconds)) {
                is CatalogOffer.Updated -> RefreshOutcome.UPDATED
                CatalogOffer.Unchanged -> RefreshOutcome.UP_TO_DATE
                CatalogOffer.PersistFailed -> RefreshOutcome.PERSIST_FAILED
                is CatalogOffer.Rejected ->
                    if (offer.reason == CatalogRejection.ROLLBACK) {
                        RefreshOutcome.UP_TO_DATE
                    } else {
                        rejection = rejection ?: offer.reason
                        RefreshOutcome.REJECTED
                    }
            }
            best = maxOf(best, outcome, PRIORITY)
        }
        return RefreshResult(best, rejection.takeIf { best == RefreshOutcome.REJECTED })
    }

    /** Null when the body exceeds [CatalogVerifier.MAX_ENVELOPE_BYTES]. */
    private fun download(url: String): ByteArray? = transport.get(url, hosts).use { body ->
        val limit = CatalogVerifier.MAX_ENVELOPE_BYTES
        if (body.contentLength > limit) return null
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = body.stream.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > limit) return null
        }
        out.toByteArray()
    }

    private companion object {
        /** Higher wins: one good mirror outweighs another mirror's failure. */
        val PRIORITY: Comparator<RefreshOutcome> = compareBy {
            when (it) {
                RefreshOutcome.NETWORK_FAILED -> 0
                RefreshOutcome.REJECTED -> 1
                RefreshOutcome.PERSIST_FAILED -> 2
                RefreshOutcome.UP_TO_DATE -> 3
                RefreshOutcome.UPDATED -> 4
            }
        }
    }
}
