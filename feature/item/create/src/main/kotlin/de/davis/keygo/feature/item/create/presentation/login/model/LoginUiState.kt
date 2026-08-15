package de.davis.keygo.feature.item.create.presentation.login.model

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.feature.item.core.presentation.model.InputFieldError
import de.davis.keygo.feature.item.create.presentation.model.ItemUiState

internal typealias LoginUiState = ItemUiState<LoginBaseState>

internal data class LoginPasskeyInfo(
    val rpId: String,
    val pending: Boolean,
)

@Stable
internal data class LoginBaseState(
    val passwordTextFieldState: TextFieldState = TextFieldState(),
    val totpTextFieldState: TextFieldState = TextFieldState(),
    val usernameTextFieldState: TextFieldState = TextFieldState(),
    val domains: Set<DomainInfo> = emptySet(),
    val passkeyRPs: Set<LoginPasskeyInfo> = emptySet(),
    val deletedPasskeyRPs: Set<String> = emptySet(),
    val strengthScore: PasswordScore = PasswordScore.None,
    val generatePasswordBottomSheetVisible: Boolean = false,
    val dialogState: DialogState = DialogState.None,
    val nameError: InputFieldError? = null,
    val scanning: Boolean = false,
    val updating: Boolean = false,
) {
    val hasAnyContent: Boolean
        get() = passwordTextFieldState.text.isNotBlank()
                || totpTextFieldState.text.isNotBlank()
                || usernameTextFieldState.text.isNotBlank()
                || passkeyRPs.isNotEmpty()

    fun canSave(name: CharSequence): Boolean = name.isNotBlank() && hasAnyContent
}
