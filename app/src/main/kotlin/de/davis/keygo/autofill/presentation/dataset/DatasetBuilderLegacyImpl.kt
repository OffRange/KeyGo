package de.davis.keygo.autofill.presentation.dataset

import android.content.IntentSender
import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.InlinePresentation
import android.widget.RemoteViews
import androidx.annotation.DeprecatedSinceApi
import de.davis.keygo.autofill.presentation.model.Extraction

@Suppress("DEPRECATION")
@DeprecatedSinceApi(Build.VERSION_CODES.TIRAMISU)
internal class DatasetBuilderLegacyImpl : DatasetBuilder {

    override fun buildDataset(
        intentSender: IntentSender,
        extraction: Extraction,
        inlinePresentation: InlinePresentation?,
        remoteViews: RemoteViews?
    ): Dataset {
        val builder = remoteViews?.let {
            Dataset.Builder(it)
        } ?: Dataset.Builder()

        return builder.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                inlinePresentation?.let { setInlinePresentation(it) }

            setAuthentication(intentSender)
            applyExtraction(extraction)
        }.build()
    }


    private fun Dataset.Builder.applyExtraction(extraction: Extraction) {
        extraction.fields.forEach {
            setValue(
                it.autofillId,
                null
            )
        }
    }
}