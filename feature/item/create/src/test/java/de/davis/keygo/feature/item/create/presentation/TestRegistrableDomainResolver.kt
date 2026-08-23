package de.davis.keygo.feature.item.create.presentation

import de.davis.keygo.core.util.domain.resolver.RegistrableDomainResolver

/** Resolves an eTLD+1 by keeping the last two labels, which is enough for the test domains. */
internal class TestRegistrableDomainResolver : RegistrableDomainResolver {
    override fun resolve(domain: String): String? {
        val labels = domain.substringAfter("://")
            .substringBefore('/')
            .split('.')
            .filter { it.isNotBlank() }

        return if (labels.size >= 2) labels.takeLast(2).joinToString(".") else null
    }
}
