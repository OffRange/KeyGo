package de.davis.keygo.autofill.presentation.dataset.menu

import android.content.Context
import android.os.Build
import android.service.autofill.Dataset
import androidx.annotation.DeprecatedSinceApi
import de.davis.keygo.R
import de.davis.keygo.autofill.presentation.dataset.DatasetBuilder
import de.davis.keygo.autofill.presentation.dataset.SuggestionFinder
import de.davis.keygo.autofill.presentation.getSelectionPendingIntent
import de.davis.keygo.autofill.presentation.model.Extraction
import de.davis.keygo.core.domain.model.Password
import org.koin.core.annotation.Single

@Single
@DeprecatedSinceApi(Build.VERSION_CODES.R)
internal class MenuDatasetBuilder(
    private val suggestionFinder: SuggestionFinder,
    private val menuDatasetBuilder: MenuSuggestionFactory,
    private val datasetBuilder: DatasetBuilder,
    private val context: Context
) {

    suspend fun buildMenuDatasets(
        extraction: Extraction
    ): List<Dataset> {
        val suggestions = suggestionFinder.findPasswordsSuggestions(extraction, count = 4)

        return suggestions.map { suggestion ->
            buildSuggestionDataset(extraction, suggestion)
        } + listOf(buildAppDataset(extraction))
    }

    private fun buildAppDataset(extraction: Extraction): Dataset {
        val remoteViews = menuDatasetBuilder.buildMenuSuggestion(
            title = context.getString(R.string.app_name),
            subtitle = context.getString(R.string.autofill_service),
            icon = R.mipmap.ic_launcher_round
        )

        return datasetBuilder.buildDataset(
            remoteViews = remoteViews,
            intentSender = context.getSelectionPendingIntent().intentSender,
            extraction = extraction,
        )
    }

    private fun buildSuggestionDataset(
        extraction: Extraction,
        suggestion: Password
    ): Dataset {
        val remoteViews = menuDatasetBuilder.buildMenuSuggestion(
            title = suggestion.name,
            subtitle = suggestion.username ?: "----",
        )

        return datasetBuilder.buildDataset(
            remoteViews = remoteViews,
            intentSender = context.getSelectionPendingIntent().intentSender,
            extraction = extraction,
        )
    }
}