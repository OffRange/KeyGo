package de.davis.keygo.feature.item.create.presentation.creditcard.model

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable
import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.feature.item.core.presentation.model.InputFieldError
import de.davis.keygo.feature.item.create.presentation.model.VaultsState

@Stable
data class CreditCardUiState(
    val nameTextFieldState: TextFieldState,
    val notesTextFieldState: TextFieldState,
    val ccHolderTextFieldState: TextFieldState,
    val ccNumberTextFieldState: TextFieldState,
    val ccCVVTextFieldState: TextFieldState,
    val ccExpirationDateTextFieldState: TextFieldState,
    val canSave: Boolean = false,
    val numberError: InputFieldError? = null,
    val vaultsState: VaultsState,
    val tagsForSuggestion: Set<Tag> = emptySet(),
    val itemAssignedTags: Set<Tag> = emptySet(),
)
