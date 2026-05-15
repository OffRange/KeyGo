package de.davis.keygo.core.feature.autofill

import de.davis.keygo.core.util.domain.resolver.RegistrableDomainResolver

/**
 * In-memory [RegistrableDomainResolver] for tests.
 *
 * Set [resolutions] to configure which domains resolve to which eTLD1 value.
 * Inspect [resolvedDomains] to verify which domains were resolved.
 */
class FakeRegistrableDomainResolver : RegistrableDomainResolver {
    // Configurable: map from domain to resolved eTLD1 (or null)
    var resolutions: Map<String, String?> = emptyMap()

    // Track calls for assertion
    val resolvedDomains: MutableList<String> = mutableListOf()

    override fun resolve(domain: String): String? {
        resolvedDomains += domain
        return resolutions[domain]
    }
}
