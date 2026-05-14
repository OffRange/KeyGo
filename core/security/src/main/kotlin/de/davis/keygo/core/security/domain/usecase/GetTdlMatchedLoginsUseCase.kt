package de.davis.keygo.core.security.domain.usecase

import de.davis.keygo.core.item.domain.model.lite.LiteLogin
import de.davis.keygo.core.item.domain.repository.LoginRepository
import de.davis.keygo.core.util.domain.resolver.RegistrableDomainResolver
import org.koin.core.annotation.Single

@Single
class GetTdlMatchedLoginsUseCase(
    private val registrableDomainResolver: RegistrableDomainResolver,
    private val loginRepository: LoginRepository,
) {

    suspend operator fun invoke(
        domain: String,
        requireTotp: Boolean = false,
        requirePassword: Boolean = false,
        limit: Int = -1,
    ): List<LiteLogin> {
        val tdl = registrableDomainResolver.resolve(domain) ?: return emptyList()
        return loginRepository.getLoginsByTLD(
            etld1 = tdl,
            requireTotp = requireTotp,
            requirePassword = requirePassword,
            limit = limit,
        )
    }
}