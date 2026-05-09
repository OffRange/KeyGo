package de.davis.keygo.feature.autofill.presentation.dataset

import de.davis.keygo.core.item.domain.model.lite.LiteLogin
import de.davis.keygo.core.item.domain.model.lite.LiteVaultItem
import de.davis.keygo.core.item.domain.repository.LoginRepository
import de.davis.keygo.core.util.domain.resolver.RegistrableDomainResolver
import de.davis.keygo.feature.autofill.presentation.model.Form
import de.davis.keygo.feature.autofill.presentation.model.FormType
import org.koin.core.annotation.Single

@Single
internal class SuggestionFinder(
    private val loginRepository: LoginRepository,
    private val registrableDomainResolver: RegistrableDomainResolver,
) {

    internal suspend fun findVaultSuggestions(
        form: Form,
        count: Int,
    ): List<LiteVaultItem> {
        if (count == 0) return emptyList()

        return when (form.type) {
            is FormType.Credentials -> findLoginSuggestions(form, count)
            is FormType.TOTP -> findLoginSuggestions(form, count, withTOTP = true)
        }
    }

    private suspend fun findLoginSuggestions(
        form: Form,
        count: Int,
        withTOTP: Boolean = false,
    ): List<LiteLogin> = form.url?.let {
        registrableDomainResolver.resolve(it)
    }?.let {
        loginRepository.getLoginsByTLD(etld1 = it, requireTotp = withTOTP, limit = count)
    } ?: emptyList()
}
