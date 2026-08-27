package de.davis.keygo.feature.autofill.presentation.activity.model

import android.content.IntentSender
import android.service.autofill.Dataset

internal sealed interface AutofillEvent {

    data object Abort : AutofillEvent

    data class Fill(val dataset: Dataset, val copyToClipboard: String? = null) : AutofillEvent

    data class RequestSmsConsent(val intentSender: IntentSender) : AutofillEvent
}