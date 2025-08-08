package de.davis.keygo.autofill.presentation.dataset

import android.content.IntentSender
import android.service.autofill.Dataset
import android.service.autofill.InlinePresentation
import de.davis.keygo.autofill.presentation.model.Extraction

internal interface DatasetBuilder {
    fun buildDataset(
        inlinePresentation: InlinePresentation,
        intentSender: IntentSender,
        extraction: Extraction
    ): Dataset
}