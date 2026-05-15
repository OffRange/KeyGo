package de.davis.keygo.feature.item.create.presentation.login.model

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.feature.item.core.presentation.model.InputFieldError
import de.davis.keygo.feature.item.create.presentation.model.VaultsState

@Stable
internal sealed interface LoginUiState {
    data object Loading : LoginUiState

    data class Ready(
        val base: LoginBaseState,
        val vaultsState: VaultsState,
    ) : LoginUiState
}

@Stable
internal data class LoginBaseState(
    val nameTextFieldState: TextFieldState = TextFieldState(),
    val notesTextFieldState: TextFieldState = TextFieldState(),
    val passwordTextFieldState: TextFieldState = TextFieldState(),
    val totpTextFieldState: TextFieldState = TextFieldState(),
    val usernameTextFieldState: TextFieldState = TextFieldState(),
    val domains: Set<DomainInfo> = emptySet(),
    val nameExists: Boolean = false,
    val strengthScore: PasswordScore = PasswordScore.None,
    val generatePasswordBottomSheetVisible: Boolean = false,
    val dialogState: DialogState = DialogState.None,
    val nameError: InputFieldError? = null,
    val existingPasskeyCount: Int = 0,
    val pendingPasskeyCount: Int = 0,
    val scanning: Boolean = false,
    val updating: Boolean = false,
) {
    val hasAnyContent: Boolean
        get() = passwordTextFieldState.text.isNotBlank()
            || totpTextFieldState.text.isNotBlank()
            || usernameTextFieldState.text.isNotBlank()
            || existingPasskeyCount > 0
            || pendingPasskeyCount > 0

    val canSave: Boolean
        get() = nameTextFieldState.text.isNotBlank() && hasAnyContent
}
