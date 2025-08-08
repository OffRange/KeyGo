package de.davis.keygo.autofill.presentation

import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.FillRequest
import androidx.annotation.ChecksSdkIntAtLeast
import de.davis.keygo.autofill.presentation.dataset.inline.InlineDatasetBuilder
import de.davis.keygo.autofill.presentation.model.Extraction
import org.koin.core.annotation.Single

@Single
internal class AutofillDatasetProvider(
    private val inlineDatasetBuilder: InlineDatasetBuilder,
) {

    suspend fun getAutofillDataset(request: FillRequest, extraction: Extraction): List<Dataset> {
        if (systemSupportsInlineSuggestions(request))
            return inlineDatasetBuilder.buildInlineDatasets(
                specs = request.inlineSuggestionsRequest!!.inlinePresentationSpecs,
                extraction = extraction
            )

        TODO()
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
}
