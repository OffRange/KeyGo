package de.davis.keygo.feature.item.create.presentation.password.model

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.feature.item.core.presentation.model.InputFieldError
import de.davis.keygo.feature.item.create.presentation.model.VaultsState

@Stable
internal sealed interface PasswordUiState {
    data object Loading : PasswordUiState

    data class Ready(
        val base: PasswordBaseState,
        val vaultsState: VaultsState,
    ) : PasswordUiState
}

@Stable
internal data class PasswordBaseState(
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
