package de.davis.keygo.feature.autofill.presentation.dataset

import android.content.IntentSender
import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.Field
import android.service.autofill.InlinePresentation
import android.service.autofill.Presentations
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import de.davis.keygo.feature.autofill.presentation.model.Form

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class DatasetBuilderApi33Impl : DatasetBuilder {

    override fun buildDataset(
        intentSender: IntentSender,
        form: Form,
        inlinePresentation: InlinePresentation?,
        remoteViews: RemoteViews?,
    ): Dataset {
        val presentation = Presentations.Builder().apply {
            inlinePresentation?.let { setInlinePresentation(it) }
            remoteViews?.let { setMenuPresentation(it) }
        }.build()

        return Dataset.Builder(presentation).apply {
            setAuthentication(intentSender)
            applyExtraction(form)
        }.build()
    }

    private fun Dataset.Builder.applyExtraction(formInformation: Form) {
        formInformation.fields.forEach {
            setField(
                it.autofillId,
                Field.Builder().build()
            )
        }
    }
}

