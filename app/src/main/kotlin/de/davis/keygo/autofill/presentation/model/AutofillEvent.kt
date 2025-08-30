package de.davis.keygo.autofill.presentation.model

import android.service.autofill.Dataset

sealed interface AutofillEvent {

    data object Abort : AutofillEvent

    data object ShowUi : AutofillEvent

    data class Fill(val dataset: Dataset) : AutofillEvent
}