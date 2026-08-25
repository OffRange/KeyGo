package de.davis.keygo.core.util

import de.davis.keygo.core.util.domain.resolver.RegistrableDomainResolver

/**
 * Answers as a resolver that can see only the last two labels of a host, which is enough for the
 * domains tests use.
 *
 * Seed [resolutions] to pin an exact answer for one domain, a null one included. A host the real
 * resolver cannot place is a case callers have to carry through, and an explicit null is the only
 * way to stage it for a host the label heuristic would otherwise resolve. Anything unseeded falls
 * back to the heuristic, so a test names only the domains it cares about.
 *
 * [resolvedDomains] records every domain asked about, in order, for tests where resolving nothing
 * at all is the behaviour under test.
 */
class FakeRegistrableDomainResolver : RegistrableDomainResolver {

    var resolutions: Map<String, String?> = emptyMap()

    val resolvedDomains: MutableList<String> = mutableListOf()

    override fun resolve(domain: String): String? {
        resolvedDomains += domain

        if (resolutions.containsKey(domain)) return resolutions[domain]

        val labels = domain.substringAfter("://")
            .substringBefore('/')
            .split('.')
            .filter { it.isNotBlank() }

        return if (labels.size >= 2) labels.takeLast(2).joinToString(".") else null
    }
}
