package de.davis.keygo.autofill.presentation.dataset

import android.content.IntentSender
import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.Field
import android.service.autofill.InlinePresentation
import android.service.autofill.Presentations
import androidx.annotation.RequiresApi
import de.davis.keygo.autofill.presentation.model.Extraction
import org.koin.core.annotation.Single

@Single
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class DatasetBuilderApi33Impl : DatasetBuilder {

    override fun buildDataset(
        inlinePresentation: InlinePresentation,
        intentSender: IntentSender,
        extraction: Extraction
    ): Dataset {
        val presentation = Presentations.Builder().apply {
            setInlinePresentation(inlinePresentation)
        }.build()

        return Dataset.Builder(presentation).apply {
            setAuthentication(intentSender)
            applyExtraction(extraction)
        }.build()
    }

    private fun Dataset.Builder.applyExtraction(extraction: Extraction) {
        extraction.fields.forEach {
            setField(
                it.autofillId,
                Field.Builder().build()
            )
        }
    }
}