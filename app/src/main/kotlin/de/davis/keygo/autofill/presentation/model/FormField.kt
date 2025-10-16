package de.davis.keygo.autofill.presentation.model

import android.os.Parcelable
import android.view.autofill.AutofillId
import kotlinx.parcelize.Parcelize

@Parcelize
data class FormField(
    val autofillId: AutofillId,
    val type: FieldType,
    val focused: Boolean,
    val url: String? = null,
    val autofillValue: String? = null,
    val requestId: Int = -1,
) : Parcelable
