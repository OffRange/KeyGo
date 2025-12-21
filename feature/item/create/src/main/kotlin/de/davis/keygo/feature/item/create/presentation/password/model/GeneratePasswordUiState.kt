package de.davis.keygo.feature.item.create.presentation.password.model

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SliderState
import de.davis.keygo.core.item.domain.model.Password

data class GeneratePasswordUiState(
    val generatedPassword: UiPassword = UiPassword(""),
    val passwordStrength: Password.Score = Password.Score.None,
    @OptIn(ExperimentalMaterial3Api::class)
    val sliderState: SliderState = SliderState(value = 10f, valueRange = 8f..100f),

    val characterSet: UiCharacterSet = UiCharacterSet.ALL,
    val showCaution: Boolean = false,
)

