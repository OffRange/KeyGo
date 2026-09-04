package de.davis.keygo.core.feature.autofill

import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.autofill.domain.model.DigitalAssetLinkFailure
import de.davis.keygo.feature.autofill.domain.repository.DigitalAssetLinkRepository

/**
 * In-memory [DigitalAssetLinkRepository] for tests.
 *
 * Set [linkedTriples] to configure which (packageName, signature, domain) combinations are linked,
 * and [failingSignatures] to make a lookup come back without a verdict. Inspect [linkedCalls] to
 * verify which combinations were queried.
 */
class FakeDigitalAssetLinkRepository : DigitalAssetLinkRepository {
    // Configurable: set of (packageName, signature, domain) triples that are "linked"
    var linkedTriples: Set<Triple<String, String, String>> = emptySet()

    // Configurable: signatures whose lookup fails with [failure] instead of returning a verdict
    var failingSignatures: Set<String> = emptySet()

    // Configurable: the failure reported for [failingSignatures]
    var failure: DigitalAssetLinkFailure = DigitalAssetLinkFailure.Unreachable

    // Track calls for assertion (e.g., fake.linkedCalls.isEmpty() or fake.linkedCalls.contains(Triple(...)))
    val linkedCalls: MutableList<Triple<String, String, String>> = mutableListOf()

    override suspend fun isLinked(
        packageName: String,
        signature: String,
        domain: String,
    ): Result<Boolean, DigitalAssetLinkFailure> {
        val call = Triple(packageName, signature, domain)
        linkedCalls += call

        if (signature in failingSignatures) return Result.Failure(failure)

        return Result.Success(call in linkedTriples)
    }
}
