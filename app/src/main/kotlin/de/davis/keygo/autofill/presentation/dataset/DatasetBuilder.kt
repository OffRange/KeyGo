package de.davis.keygo.autofill.presentation.dataset

import android.content.IntentSender
import android.service.autofill.Dataset
import android.service.autofill.InlinePresentation
import android.widget.RemoteViews
import de.davis.keygo.autofill.presentation.model.Extraction

internal interface DatasetBuilder {
    fun buildDataset(
        intentSender: IntentSender,
        extraction: Extraction,
        inlinePresentation: InlinePresentation? = null,
        remoteViews: RemoteViews? = null,
    ): Dataset
}