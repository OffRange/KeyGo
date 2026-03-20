package de.davis.keygo.feature.autofill.domain.usecase

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.repository.PasswordRepository
import de.davis.keygo.core.util.domain.resolver.RegistrableDomainResolver
import org.koin.core.annotation.Single

@Single
class DoesItemHaveDomainReferencesUseCase(
    private val passwordRepository: PasswordRepository,
    private val registrableDomainResolver: RegistrableDomainResolver
) {

    suspend operator fun invoke(itemVaultId: ItemId, domain: String): Boolean {
        if (domain.isBlank()) return false

        val passwordDomains = passwordRepository.getPasswordById(itemVaultId)
            ?.domainInfos
            ?: return false

        val eTLD1ToCheck = registrableDomainResolver.resolve(domain)

        return passwordDomains.any { it.value == domain || it.eTLD1 == eTLD1ToCheck }
    }
}