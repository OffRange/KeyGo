package de.davis.keygo.autofill.presentation

import android.content.Context
import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.Field
import android.service.autofill.FillRequest
import android.service.autofill.Presentations
import android.widget.RemoteViews
import androidx.annotation.ChecksSdkIntAtLeast
import de.davis.keygo.autofill.presentation.dataset.inline.InlineDatasetBuilder
import de.davis.keygo.autofill.presentation.dataset.menu.MenuDatasetBuilder
import de.davis.keygo.autofill.presentation.model.AutofillValue
import de.davis.keygo.autofill.presentation.model.Form
import org.koin.core.annotation.Single
import android.view.autofill.AutofillValue as AndroidAutofillValue

@Single
internal class AutofillDatasetProvider(
    applicationContext: Context,
    private val inlineDatasetBuilder: InlineDatasetBuilder,
    private val menuDatasetBuilder: MenuDatasetBuilder,
) {

    suspend fun getAutofillDatasets(
        request: FillRequest,
        form: Form
    ): List<Dataset> {
        if (systemSupportsInlineSuggestions(request))
            return inlineDatasetBuilder.buildInlineDatasets(
                specs = request.inlineSuggestionsRequest!!.inlinePresentationSpecs,
                form = form
            )

        return menuDatasetBuilder.buildMenuDatasets(form = form)
    }

    fun getFillingDataset(values: List<AutofillValue>) = getDefaultDataset()
        .applyValues(values)
        .build()

    private fun getDefaultDataset() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Dataset.Builder(
            Presentations.Builder()
                .setMenuPresentation(dummyRemoteViews)
                .build()
        )
    else Dataset.Builder()

    private fun Dataset.Builder.applyValues(values: List<AutofillValue>) = apply {
        values.forEach {
            val value = AndroidAutofillValue.forText(it.value)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                setField(
                    it.autofillId,
                    Field.Builder()
                        .setValue(value)
                        .build()
                )
            } else {
                @Suppress("DEPRECATION")
                setValue(
                    it.autofillId,
                    value,
                    dummyRemoteViews
                )
            }
        }
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    private fun systemSupportsInlineSuggestions(request: FillRequest): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            request.inlineSuggestionsRequest?.let {
                val maxSuggestion = it.maxSuggestionCount
                val specCount = it.inlinePresentationSpecs.count()
                maxSuggestion > 0 && specCount > 0
            } ?: false
        else false

    private val dummyRemoteViews: RemoteViews =
        RemoteViews(
            applicationContext.packageName,
            android.R.layout.simple_list_item_1
        )
}
