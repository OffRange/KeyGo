package de.davis.keygo.feature.item.view.password.model

import de.davis.keygo.feature.item.core.presentation.model.InputFieldError
import de.davis.keygo.feature.item.core.presentation.password.model.FieldType

data class ModificationDialog(
    val fieldType: FieldType,
    val initialValue: String,
    val error: InputFieldError? = null,
)