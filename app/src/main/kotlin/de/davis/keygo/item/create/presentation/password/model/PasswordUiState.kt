package de.davis.keygo.item.create.presentation.password.model

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import de.davis.keygo.core.domain.model.Score
import de.davis.keygo.core.presentation.model.InputFieldError

data class PasswordUiState(
    val nameTextFieldState: TextFieldState = TextFieldState(),
    val notesTextFieldState: TextFieldState = TextFieldState(),
    val passwordTextFieldState: TextFieldState = TextFieldState(),
    val totpTextFieldState: TextFieldState = TextFieldState(),
    val usernameTextFieldState: TextFieldState = TextFieldState(),
    val websiteTextFieldState: TextFieldState = TextFieldState(),
    val strengthScore: Score = Score.None,
    val generatePasswordBottomSheetVisible: Boolean = false,
    @OptIn(ExperimentalMaterial3Api::class)
    val generatePasswordState: GeneratePasswordUiState = GeneratePasswordUiState(),
    val dialogState: DialogState = DialogState.None,
    val nameError: InputFieldError? = null,
    val passwordError: InputFieldError? = null,
    val scanning: Boolean = false,
    val updating: Boolean = false,
)
