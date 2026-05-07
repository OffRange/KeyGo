package de.davis.keygo.feature.item.create.presentation.password.model

import de.davis.keygo.core.item.domain.model.Login

internal data class GeneratePasswordUiState(
    val generatedPassword: UiPassword = UiPassword(""),
    val passwordStrength: Login.Score = Login.Score.None,

    val characterSet: UiCharacterSet = UiCharacterSet.ALL,
    val showCaution: Boolean = false,
)

