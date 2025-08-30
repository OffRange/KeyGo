package de.davis.keygo.autofill.presentation.dataset

import de.davis.keygo.autofill.presentation.model.Form
import de.davis.keygo.autofill.presentation.model.FormType
import de.davis.keygo.core.domain.model.Password
import de.davis.keygo.core.domain.model.VaultItem
import de.davis.keygo.core.domain.repository.PasswordRepository
import org.koin.core.annotation.Single

@Single
internal class SuggestionFinder(
    private val passwordRepository: PasswordRepository,
) {

    internal suspend fun findVaultSuggestions(
        form: Form,
        count: Int
    ): List<VaultItem> {
        if (count == 0) return emptyList()

        return when (form.type) {
            is FormType.Credentials -> findPasswordSuggestions(form, count)
        }
    }

    private suspend fun findPasswordSuggestions(
        form: Form,
        count: Int
    ): List<Password> = form.urls.flatMap {
        passwordRepository.findVaultPasswordsByUrl(url = it)
    }.take(count)
}
