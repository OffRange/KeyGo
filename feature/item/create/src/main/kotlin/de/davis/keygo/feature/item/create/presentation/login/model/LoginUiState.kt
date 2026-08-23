package de.davis.keygo.feature.item.create.presentation.login.model

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.PasskeyRef
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.feature.item.core.presentation.model.InputFieldError
import de.davis.keygo.feature.item.create.presentation.model.ItemUiState

internal typealias LoginUiState = ItemUiState<LoginBaseState>

/**
 * One passkey chip. There is one per credential, not per relying party: a login can hold two
 * credentials for the same site and the user has to be able to tell them apart and drop one.
 *
 * [ref] is null while the passkey activity is still registering, because a credential id only
 * exists once the item it belongs to does.
 */
internal data class LoginPasskeyInfo(
    val rpId: String,
    val ref: PasskeyRef?,
) {
    val pending: Boolean
        get() = ref == null
}

@Stable
internal data class LoginBaseState(
    val passwordTextFieldState: TextFieldState = TextFieldState(),
    val totpTextFieldState: TextFieldState = TextFieldState(),
    val usernameTextFieldState: TextFieldState = TextFieldState(),
    val domains: Set<DomainInfo> = emptySet(),
    val passkeys: Set<LoginPasskeyInfo> = emptySet(),
    val deletedPasskeys: Set<PasskeyRef> = emptySet(),
    val strengthScore: PasswordScore = PasswordScore.None,
    val generatePasswordBottomSheetVisible: Boolean = false,
    val dialogState: DialogState = DialogState.None,
    val nameError: InputFieldError? = null,
    val scanning: Boolean = false,
    /**
     * Whether a deep-linked code is being imported, from the picker until the item is saved.
     *
     * The screen has to claim back for as long as this runs: the form was opened by the picker, so
     * back belongs to that flow rather than to the pane the screen sits in.
     */
    val totpImportActive: Boolean = false,
    val selectingItemForTotp: Boolean = false,
    val totpSuggestedItemIds: Set<ItemId> = emptySet(),
    val updating: Boolean = false,
) {
    /**
     * Whether the form holds anything worth saving.
     *
     * A pending passkey deletion deliberately does not count. A login whose last passkey is gone
     * holds nothing, and there is no reason to keep it, so Save stays disabled exactly as it does
     * when the last password or TOTP secret is cleared. Leaving the screen without saving keeps the
     * passkey.
     */
    val hasAnyContent: Boolean
        get() = passwordTextFieldState.text.isNotBlank()
                || totpTextFieldState.text.isNotBlank()
                || usernameTextFieldState.text.isNotBlank()
                || passkeys.isNotEmpty()

    fun canSave(name: CharSequence): Boolean = name.isNotBlank() && hasAnyContent
}
