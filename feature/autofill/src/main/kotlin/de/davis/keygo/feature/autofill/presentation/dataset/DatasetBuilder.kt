package de.davis.keygo.feature.autofill.presentation.dataset

import android.content.IntentSender
import android.service.autofill.Dataset
import android.service.autofill.InlinePresentation
import android.widget.RemoteViews
import de.davis.keygo.feature.autofill.presentation.model.Form

internal interface DatasetBuilder {
    fun buildDataset(
        intentSender: IntentSender,
        form: Form,
        inlinePresentation: InlinePresentation? = null,
        remoteViews: RemoteViews? = null,
    ): Dataset
}