package de.davis.keygo.viewing.presentation.model

import androidx.compose.foundation.text.input.TextFieldState
import de.davis.keygo.core.presentation.model.InputFieldError

data class ModificationDialog(
    val fieldType: FieldType,
    val textFieldState: TextFieldState,
    val error: InputFieldError? = null,
)