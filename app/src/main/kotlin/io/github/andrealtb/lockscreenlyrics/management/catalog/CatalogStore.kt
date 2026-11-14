package io.github.andrealtb.lockscreenlyrics.management.catalog

import java.io.IOException

/** What the client last accepted: the revision floor plus the exact envelope bytes. */
internal class PersistedCatalog(val accepted: AcceptedRevision, val envelope: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is PersistedCatalog && other.accepted == accepted && other.envelope.contentEquals(envelope)

    override fun hashCode(): Int = 31 * accepted.hashCode() + envelope.contentHashCode()
}

internal interface CatalogPersistence {
    /** Null when nothing was stored or the stored state is unreadable. */
    @Throws(IOException::class)
    fun read(): PersistedCatalog?

    /** Must replace the previous state atomically. */
    @Throws(IOException::class)
    fun write(value: PersistedCatalog)
}

internal enum class CatalogSource { PERSISTED, BUNDLED, REMOTE }

internal sealed interface CatalogState {
    /** No verified catalog at all: the store shows installed apps only. */
    data object Unavailable : CatalogState

    data class Current(
        val catalog: Catalog,
        val freshness: CatalogFreshness,
        val source: CatalogSource,
    ) : CatalogState {
        /** Installs and updates need a fresh catalog; an expired one is display-only (§6.2). */
        val installable: Boolean get() = freshness == CatalogFreshness.FRESH
    }
}

internal sealed interface CatalogOffer {
    data class Updated(val state: CatalogState.Current) : CatalogOffer

    /** Same revision and bytes as the one already accepted (e.g. another mirror). */
    data object Unchanged : CatalogOffer

    data class Rejected(val reason: CatalogRejection, val detail: String) : CatalogOffer

    /** Valid, but the new floor could not be stored; the catalog is not used. */
    data object PersistFailed : CatalogOffer
}

/**
 * Chooses the catalog the store works from (plan §6.2): the persisted catalog, the signed
 * snapshot bundled with this Bridge build, or a newly fetched one. Every candidate is verified
 * against the persisted revision floor, so neither a mirror nor an older bundled snapshot can
 * roll the store back. The floor only ever increases and is written before the new catalog is
 * used. Pure JVM; the caller owns threading, networking and time.
 */
internal class CatalogStore(
    private val verifier: CatalogVerifier,
    private val persistence: CatalogPersistence,
    private val bundledSnapshot: () -> ByteArray?,
) {
    fun load(nowEpochSeconds: Long): CatalogState {
        val persisted = readPersisted()
        val floor = persisted?.accepted
        val candidates = buildList {
            persisted?.let { accept(it.envelope, nowEpochSeconds, floor)?.let { a -> add(Candidate(a, it.envelope, CatalogSource.PERSISTED)) } }
            readBundled()?.let { bytes -> accept(bytes, nowEpochSeconds, floor)?.let { a -> add(Candidate(a, bytes, CatalogSource.BUNDLED)) } }
        }
        // Higher revision wins; on a tie the persisted copy is the same bytes (digest checked).
        val best = candidates.maxWithOrNull(
            compareBy<Candidate> { it.accepted.accepted.revision }.thenBy { it.source == CatalogSource.PERSISTED },
        ) ?: return CatalogState.Unavailable
        if (floor == null || best.accepted.accepted.revision > floor.revision) {
            if (!tryPersist(PersistedCatalog(best.accepted.accepted, best.bytes))) {
                // Cannot raise the floor: keep using what was already trusted, if anything.
                return candidates.firstOrNull { it.source == CatalogSource.PERSISTED }?.state()
                    ?: CatalogState.Unavailable
            }
        }
        return best.state()
    }

    fun offer(envelope: ByteArray, nowEpochSeconds: Long): CatalogOffer {
        val floor = readPersisted()?.accepted
        return when (val result = verifier.verify(envelope, nowEpochSeconds, floor)) {
            is CatalogVerification.Rejected -> CatalogOffer.Rejected(result.reason, result.detail)
            is CatalogVerification.Accepted -> when {
                floor != null && result.accepted == floor -> CatalogOffer.Unchanged
                !tryPersist(PersistedCatalog(result.accepted, envelope)) -> CatalogOffer.PersistFailed
                else -> CatalogOffer.Updated(CatalogState.Current(result.catalog, result.freshness, CatalogSource.REMOTE))
            }
        }
    }

    private data class Candidate(
        val accepted: CatalogVerification.Accepted,
        val bytes: ByteArray,
        val source: CatalogSource,
    ) {
        fun state() = CatalogState.Current(accepted.catalog, accepted.freshness, source)
    }

    private fun accept(bytes: ByteArray, now: Long, floor: AcceptedRevision?): CatalogVerification.Accepted? =
        verifier.verify(bytes, now, floor) as? CatalogVerification.Accepted

    private fun readPersisted(): PersistedCatalog? = try {
        persistence.read()
    } catch (_: IOException) {
        null
    }

    private fun readBundled(): ByteArray? = try {
        bundledSnapshot()
    } catch (_: IOException) {
        null
    }

    private fun tryPersist(value: PersistedCatalog): Boolean = try {
        persistence.write(value)
        true
    } catch (_: IOException) {
        false
    }
}
