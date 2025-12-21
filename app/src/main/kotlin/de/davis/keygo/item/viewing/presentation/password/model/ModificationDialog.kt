package de.davis.keygo.item.viewing.presentation.password.model

import androidx.compose.foundation.text.input.TextFieldState
import de.davis.keygo.core.presentation.model.InputFieldError
import de.davis.keygo.feature.item.core.presentation.password.model.FieldType

data class ModificationDialog(
    val fieldType: FieldType,
    val textFieldState: TextFieldState,
    val error: InputFieldError? = null,
)