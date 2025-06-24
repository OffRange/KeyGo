package de.davis.keygo.viewing.presentation.model

import androidx.compose.foundation.text.input.TextFieldState

data class ModificationDialog(
    val fieldType: FieldType,
    val textFieldState: TextFieldState
)