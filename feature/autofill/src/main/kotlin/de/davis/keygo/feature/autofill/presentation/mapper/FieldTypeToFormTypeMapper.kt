package de.davis.keygo.feature.autofill.presentation.mapper

import de.davis.keygo.feature.autofill.presentation.model.FieldType
import de.davis.keygo.feature.autofill.presentation.model.FormType

internal fun FieldType.toFormType(): FormType = when (this) {
    is FieldType.Credentials -> FormType.Credentials
    is FieldType.TOTP -> FormType.TOTP
    else -> throw IllegalArgumentException("Cannot convert $this to FormType")
}