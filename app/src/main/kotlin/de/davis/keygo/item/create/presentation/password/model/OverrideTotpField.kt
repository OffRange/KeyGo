package de.davis.keygo.item.create.presentation.password.model

import de.davis.keygo.item.core.presentation.password.model.FieldType

data class OverrideTotpField(
    val fieldType: FieldType,
    val before: String,
    val after: String,
    val selected: Boolean = true
)