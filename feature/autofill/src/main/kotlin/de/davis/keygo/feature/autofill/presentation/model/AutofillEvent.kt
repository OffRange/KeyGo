package de.davis.keygo.feature.autofill.presentation.model

import android.service.autofill.Dataset

internal sealed interface AutofillEvent {

    data object Abort : AutofillEvent

    data class Fill(val dataset: Dataset) : AutofillEvent
}