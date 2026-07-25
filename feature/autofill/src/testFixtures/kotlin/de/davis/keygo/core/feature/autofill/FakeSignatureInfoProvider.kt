package de.davis.keygo.core.feature.autofill

import de.davis.keygo.feature.autofill.domain.SignatureInfoProvider

/**
 * In-memory [SignatureInfoProvider] for tests.
 *
 * Set [signatures] to configure which package names map to which certificate signature sets.
 * Returns an empty set for any package name not in [signatures].
 */
class FakeSignatureInfoProvider : SignatureInfoProvider {
    // Configurable: packageName -> set of signatures (default: empty = no signatures)
    var signatures: Map<String, Set<String>> = emptyMap()

    override fun getSignatureInfo(packageName: String): Set<String> =
        signatures[packageName] ?: emptySet()
}
