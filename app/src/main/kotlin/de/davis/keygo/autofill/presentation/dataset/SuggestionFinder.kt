package de.davis.keygo.autofill.presentation.dataset

import de.davis.keygo.autofill.presentation.model.Form
import de.davis.keygo.autofill.presentation.model.FormType
import de.davis.keygo.core.item.domain.model.lite.LitePassword
import de.davis.keygo.core.item.domain.model.lite.LiteVaultItem
import de.davis.keygo.core.item.domain.repository.PasswordRepository
import de.davis.keygo.core.util.domain.resolver.RegistrableDomainResolver
import org.koin.core.annotation.Single

@Single
internal class SuggestionFinder(
    private val passwordRepository: PasswordRepository,
    private val registrableDomainResolver: RegistrableDomainResolver
) {

    internal suspend fun findVaultSuggestions(
        form: Form,
        count: Int
    ): List<LiteVaultItem> {
        if (count == 0) return emptyList()

        return when (form.type) {
            is FormType.Credentials -> findPasswordSuggestions(form, count)
        }
    }

    private suspend fun findPasswordSuggestions(
        form: Form,
        count: Int
    ): List<LitePassword> = form.url?.let {
        registrableDomainResolver.resolve(it)
    }?.let {
        passwordRepository.getVaultPasswordsByTLD(etld1 = it, limit = count)
    } ?: emptyList()
}
