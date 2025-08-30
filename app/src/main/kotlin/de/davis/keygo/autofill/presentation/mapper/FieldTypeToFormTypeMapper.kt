package de.davis.keygo.autofill.presentation.mapper

import de.davis.keygo.autofill.presentation.model.FieldType
import de.davis.keygo.autofill.presentation.model.FormType

fun FieldType.toFormType(): FormType = when (this) {
    is FieldType.Credentials -> FormType.Credentials
    else -> throw IllegalArgumentException("Cannot convert $this to FormType")
}