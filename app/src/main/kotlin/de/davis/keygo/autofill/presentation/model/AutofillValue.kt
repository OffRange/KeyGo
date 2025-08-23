package de.davis.keygo.autofill.presentation.model

import android.view.autofill.AutofillId

data class AutofillValue(
    val autofillId: AutofillId,
    val value: String
)
