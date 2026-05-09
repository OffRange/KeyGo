package de.davis.keygo.feature.autofill.domain.usecase

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.repository.LoginRepository
import de.davis.keygo.core.util.domain.resolver.RegistrableDomainResolver
import org.koin.core.annotation.Single

@Single
class AddRegistrableDomainsToLoginUseCase(
    private val loginRepository: LoginRepository,
    private val registrableDomainResolver: RegistrableDomainResolver,
) {

    suspend operator fun invoke(loginId: ItemId, domain: String) {
        val domainInfo = DomainInfo(
            loginId = loginId,
            value = domain,
            eTLD1 = registrableDomainResolver.resolve(domain),
        )

        loginRepository.updateDomainInfos(loginId, setOf(domainInfo))
    }
}
