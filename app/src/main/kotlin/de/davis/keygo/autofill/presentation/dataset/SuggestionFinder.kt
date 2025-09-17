package de.davis.keygo.autofill.presentation.dataset

import de.davis.keygo.autofill.presentation.model.Form
import de.davis.keygo.autofill.presentation.model.FormType
import de.davis.keygo.core.item.domain.model.lite.LitePassword
import de.davis.keygo.core.item.domain.model.lite.LiteVaultItem
import de.davis.keygo.core.item.domain.repository.PasswordRepository
import org.koin.core.annotation.Single

@Single
internal class SuggestionFinder(
    private val passwordRepository: PasswordRepository,
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
    ): List<LitePassword> = form.urls.flatMap {
        // TODO: introduce use case to get right eTLD+1, also migrate to use limit
        passwordRepository.getVaultPasswordsByTLDs(etld1s = form.urls)
    }.take(count)
}
