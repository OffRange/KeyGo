package de.davis.keygo.feature.item.create.presentation.password.model

import de.davis.keygo.core.item.domain.model.Password

internal data class GeneratePasswordUiState(
    val generatedPassword: UiPassword = UiPassword(""),
    val passwordStrength: Password.Score = Password.Score.None,

    val characterSet: UiCharacterSet = UiCharacterSet.ALL,
    val showCaution: Boolean = false,
)

