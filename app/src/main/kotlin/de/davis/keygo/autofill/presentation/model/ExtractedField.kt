package de.davis.keygo.autofill.presentation.model

import android.os.Parcelable
import android.view.autofill.AutofillId
import kotlinx.parcelize.Parcelize

@Parcelize
data class ExtractedField(
    val autofillId: AutofillId,
    val features: FieldFeatures,
    val type: FieldType,
) : Parcelable