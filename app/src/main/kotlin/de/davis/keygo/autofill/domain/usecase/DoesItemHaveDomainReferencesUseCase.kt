package de.davis.keygo.autofill.domain.usecase

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.repository.PasswordRepository
import de.davis.keygo.core.util.domain.resolver.RegistrableDomainResolver
import org.koin.core.annotation.Single

@Single
class DoesItemHaveDomainReferencesUseCase(
    private val passwordRepository: PasswordRepository,
    private val registrableDomainResolver: RegistrableDomainResolver
) {

    suspend operator fun invoke(itemVaultId: ItemId, domains: Set<String>): Boolean {
        val passwordDomains = passwordRepository.getPasswordById(itemVaultId)
            ?.domainInfos
            ?: return false

        val eTLD1sToCheck = domains.mapNotNull { registrableDomainResolver.resolve(it) }

        return passwordDomains.any { it.value in domains || it.eTLD1 in eTLD1sToCheck }
    }
}