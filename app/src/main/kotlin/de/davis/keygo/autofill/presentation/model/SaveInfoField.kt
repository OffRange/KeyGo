package de.davis.keygo.autofill.presentation.model

import android.os.Parcelable
import android.view.autofill.AutofillId
import kotlinx.parcelize.Parcelize

@Parcelize
data class SaveInfoField(
    val autofillId: AutofillId,
    val requestId: Int
) : Parcelable
