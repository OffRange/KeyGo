package de.davis.keygo.feature.autofill.domain.usecase

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.repository.PasswordRepository
import de.davis.keygo.core.util.domain.resolver.RegistrableDomainResolver
import org.koin.core.annotation.Single

@Single
class AddRegistrableDomainsToPasswordUseCase(
    private val passwordRepository: PasswordRepository,
    private val registrableDomainResolver: RegistrableDomainResolver
) {

    suspend operator fun invoke(passwordId: ItemId, domain: String) {
        val domainInfo = DomainInfo(
            passwordId = passwordId,
            value = domain,
            eTLD1 = registrableDomainResolver.resolve(domain)
        )

        passwordRepository.updateDomainInfos(passwordId, setOf(domainInfo))
    }
}