package de.davis.keygo.core.feature.autofill

import de.davis.keygo.feature.autofill.domain.repository.DigitalAssetLinkRepository

/**
 * In-memory [DigitalAssetLinkRepository] for tests.
 *
 * Set [linkedTriples] to configure which (packageName, signature, domain) combinations are linked.
 * Inspect [linkedCalls] to verify which combinations were queried.
 */
class FakeDigitalAssetLinkRepository : DigitalAssetLinkRepository {
    // Configurable: set of (packageName, signature, domain) triples that are "linked"
    var linkedTriples: Set<Triple<String, String, String>> = emptySet()

    // Track calls for assertion (e.g., fake.linkedCalls.isEmpty() or fake.linkedCalls.contains(Triple(...)))
    val linkedCalls: MutableList<Triple<String, String, String>> = mutableListOf()

    override suspend fun isLinked(packageName: String, signature: String, domain: String): Boolean {
        linkedCalls += Triple(packageName, signature, domain)
        return Triple(packageName, signature, domain) in linkedTriples
    }
}
