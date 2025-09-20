package de.davis.keygo.core.util.domain.resolver

interface RegistrableDomainResolver {

    fun resolve(domain: String): String?
}