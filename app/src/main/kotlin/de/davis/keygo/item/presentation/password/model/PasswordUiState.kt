package de.davis.keygo.item.presentation.password.model

import androidx.compose.foundation.text.input.TextFieldState
import de.davis.keygo.item.domain.model.Strength

data class PasswordUiState(
    val nameTextFieldState: TextFieldState = TextFieldState(),
    val notesTextFieldState: TextFieldState = TextFieldState(),
    val passwordTextFieldState: TextFieldState = TextFieldState(),
    val usernameTextFieldState: TextFieldState = TextFieldState(),
    val websiteTextFieldState: TextFieldState = TextFieldState(),
    val strengthState: StrengthUiState = StrengthUiState(),
)

data class StrengthUiState(
    val score: Strength.Score = Strength.Score.None,
    val hintAvailable: Boolean = false,
)