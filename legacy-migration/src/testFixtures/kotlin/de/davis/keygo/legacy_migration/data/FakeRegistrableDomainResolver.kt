package de.davis.keygo.legacy_migration.data

import de.davis.keygo.core.util.domain.resolver.RegistrableDomainResolver

/**
 * Resolves anything under `example` to `example.com` and everything else to null.
 *
 * Every origin the migration tests seed is an example.com URL, so a fixed answer keeps eTLD+1
 * resolution from becoming a second thing that can fail in a test about the import. The null branch
 * is still real: it is what a host the resolver cannot place answers with, and the converter has to
 * carry that through as a domain info without an eTLD+1 rather than dropping the origin.
 */
internal class FakeRegistrableDomainResolver : RegistrableDomainResolver {

    override fun resolve(domain: String): String? =
        if (domain.contains("example")) "example.com" else null
}
