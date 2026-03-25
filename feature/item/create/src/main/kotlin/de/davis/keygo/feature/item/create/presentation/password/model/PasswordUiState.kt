package de.davis.keygo.feature.item.create.presentation.password.model

import androidx.compose.foundation.text.input.TextFieldState
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.feature.item.core.presentation.model.InputFieldError

internal data class PasswordUiState(
    val nameTextFieldState: TextFieldState = TextFieldState(),
    val notesTextFieldState: TextFieldState = TextFieldState(),
    val passwordTextFieldState: TextFieldState = TextFieldState(),
    val totpTextFieldState: TextFieldState = TextFieldState(),
    val usernameTextFieldState: TextFieldState = TextFieldState(),
    val domains: Set<DomainInfo> = emptySet(),
    val nameExists: Boolean = false,
    val strengthScore: Password.Score = Password.Score.None,
    val generatePasswordBottomSheetVisible: Boolean = false,
    val dialogState: DialogState = DialogState.None,
    val nameError: InputFieldError? = null,
    val passwordError: InputFieldError? = null,
    val scanning: Boolean = false,
    val updating: Boolean = false,
)
