package de.davis.keygo.feature.autofill.presentation.model

import android.view.autofill.AutofillId

internal data class AutofillValue(
    val autofillId: AutofillId,
    val value: String
)
