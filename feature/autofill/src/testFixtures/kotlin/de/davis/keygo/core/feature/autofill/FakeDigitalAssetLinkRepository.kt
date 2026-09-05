package de.davis.keygo.core.feature.autofill

import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.autofill.domain.model.DigitalAssetLinkFailure
import de.davis.keygo.feature.autofill.domain.repository.DigitalAssetLinkRepository
import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory [DigitalAssetLinkRepository] for tests.
 *
 * Set [links] to configure which app proves which domain with which signature, and [failingDomains]
 * to make a domain answer with [failure] instead of a verdict. Setting [gate] makes each lookup
 * suspend on it first, which lets a test hold one open and check what is on screen meanwhile.
 * Inspect [lookups] to see what was asked.
 */
class FakeDigitalAssetLinkRepository : DigitalAssetLinkRepository {

    /** One statement a site publishes: [signature] proves [packageName] owns [domain]. */
    data class Link(
        val packageName: String,
        val domain: String,
        val signature: String,
    )

    /** A lookup answers true when any queried signature matches one of these. */
    var links: Set<Link> = emptySet()

    /** Domains whose statement list cannot be read; they answer with [failure]. */
    var failingDomains: Set<String> = emptySet()

    /** The failure reported for [failingDomains]. */
    var failure: DigitalAssetLinkFailure = DigitalAssetLinkFailure.Unreachable

    /** When set, every lookup suspends on it before answering. */
    var gate: CompletableDeferred<Unit>? = null

    /** Every lookup, in order, for assertions such as `lookups.isEmpty()`. */
    val lookups: MutableList<Lookup> = mutableListOf()

    data class Lookup(
        val packageName: String,
        val domain: String,
        val signatures: Set<String>,
    )

    override suspend fun isLinked(
        packageName: String,
        domain: String,
        signatures: Set<String>,
    ): Result<Boolean, DigitalAssetLinkFailure> {
        lookups += Lookup(packageName, domain, signatures)
        gate?.await()

        if (domain in failingDomains) return Result.Failure(failure)

        return Result.Success(
            signatures.any { Link(packageName, domain, it) in links },
        )
    }
}
