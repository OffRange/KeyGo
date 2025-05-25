package de.davis.keygo.item.presentation.password.model

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import de.davis.keygo.item.presentation.model.InputFieldError
import de.davis.keygo.item.domain.model.Score

data class PasswordUiState(
    val nameTextFieldState: TextFieldState = TextFieldState(),
    val notesTextFieldState: TextFieldState = TextFieldState(),
    val passwordTextFieldState: TextFieldState = TextFieldState(),
    val usernameTextFieldState: TextFieldState = TextFieldState(),
    val websiteTextFieldState: TextFieldState = TextFieldState(),
    val strengthScore: Score = Score.None,
    val generatePasswordBottomSheetVisible: Boolean = false,
    @OptIn(ExperimentalMaterial3Api::class)
    val generatePasswordState: GeneratePasswordUiState = GeneratePasswordUiState(),
    val nameError: InputFieldError? = null,
    val passwordError: InputFieldError? = null,
)