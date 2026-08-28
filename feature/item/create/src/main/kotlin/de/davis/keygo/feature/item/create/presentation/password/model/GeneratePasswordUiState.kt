package de.davis.keygo.feature.item.create.presentation.password.model

import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.feature.item.core.presentation.login.model.UiPassword

internal data class GeneratePasswordUiState(
    val generatedPassword: UiPassword = UiPassword(""),
    val passwordStrength: PasswordScore = PasswordScore.None,

    val characterSet: UiCharacterSet = UiCharacterSet.ALL,
    val showCaution: Boolean = false,
)

