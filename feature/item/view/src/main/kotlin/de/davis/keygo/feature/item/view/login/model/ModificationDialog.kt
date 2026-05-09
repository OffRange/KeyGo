package de.davis.keygo.feature.item.view.login.model

import de.davis.keygo.feature.item.core.presentation.login.model.FieldType
import de.davis.keygo.feature.item.core.presentation.model.InputFieldError

data class ModificationDialog(
    val fieldType: FieldType,
    val initialValue: String,
    val error: InputFieldError? = null,
)
