package de.davis.keygo.feature.item.create.presentation.creditcard.model

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable
import de.davis.keygo.feature.item.core.presentation.model.InputFieldError
import de.davis.keygo.feature.item.create.presentation.model.ItemUiState

internal typealias CreditCardUiState = ItemUiState<CreditCardBaseState>

@Stable
internal data class CreditCardBaseState(
    val ccHolderTextFieldState: TextFieldState = TextFieldState(),
    val ccNumberTextFieldState: TextFieldState = TextFieldState(),
    val ccCVVTextFieldState: TextFieldState = TextFieldState(),
    val ccExpirationDateTextFieldState: TextFieldState = TextFieldState(),
    val numberInputTransformation: InputTransformation? = null,
    val cvvInputTransformation: InputTransformation? = null,
    val numberError: InputFieldError? = null,
    val updating: Boolean = false,
) {
    fun canSave(name: CharSequence): Boolean =
        name.isNotBlank() && ccNumberTextFieldState.text.isNotBlank()
}
