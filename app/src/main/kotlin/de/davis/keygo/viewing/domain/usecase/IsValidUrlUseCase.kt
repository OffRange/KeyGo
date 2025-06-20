package de.davis.keygo.viewing.domain.usecase

import org.koin.core.annotation.Single

@Single
class IsValidUrlUseCase {

    operator fun invoke(url: String): Boolean = url.matches(URL_REGEX)

    companion object {
        private val URL_REGEX =
            "^(https?://)?(?:[A-Za-z0-9-]+\\.)+[A-Za-z]{2,}(?::\\d{1,5})?(?:/\\S*)?$"
                .toRegex(RegexOption.IGNORE_CASE)
    }
}