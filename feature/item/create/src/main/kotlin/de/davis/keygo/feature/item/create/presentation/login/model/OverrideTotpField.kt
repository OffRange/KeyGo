package de.davis.keygo.feature.item.create.presentation.login.model

import de.davis.keygo.feature.item.core.presentation.login.model.FieldType

data class OverrideTotpField(
    val fieldType: FieldType,
    val before: String,
    val after: String,
    val selected: Boolean = true,
)
