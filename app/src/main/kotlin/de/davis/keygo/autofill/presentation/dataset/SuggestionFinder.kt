package de.davis.keygo.autofill.presentation.dataset

import de.davis.keygo.autofill.presentation.model.Extraction
import de.davis.keygo.core.domain.model.Password
import de.davis.keygo.core.domain.repository.PasswordRepository
import org.koin.core.annotation.Single

@Single
internal class SuggestionFinder(
    private val passwordRepository: PasswordRepository,
) {

    internal suspend fun findPasswordsSuggestions(
        extraction: Extraction,
        count: Int
    ): List<Password> {
        if (count == 0) return emptyList()
        return extraction.urls.flatMap {
            passwordRepository.findVaultPasswordsByUrl(url = it)
        }.take(count)
    }
}
