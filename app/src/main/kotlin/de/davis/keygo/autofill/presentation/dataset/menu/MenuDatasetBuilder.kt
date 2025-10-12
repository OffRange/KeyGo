package de.davis.keygo.autofill.presentation.dataset.menu

import android.content.Context
import android.os.Build
import android.service.autofill.Dataset
import androidx.annotation.DeprecatedSinceApi
import de.davis.keygo.R
import de.davis.keygo.autofill.presentation.dataset.DatasetBuilder
import de.davis.keygo.autofill.presentation.dataset.SuggestionFinder
import de.davis.keygo.autofill.presentation.getSelectionPendingIntent
import de.davis.keygo.autofill.presentation.model.Form
import de.davis.keygo.autofill.presentation.model.FormType
import de.davis.keygo.autofill.presentation.model.appRequestData
import de.davis.keygo.autofill.presentation.model.suggestionRequestData
import de.davis.keygo.autofill.presentation.subtitle
import de.davis.keygo.core.item.domain.model.lite.LiteVaultItem
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
        form: Form
    ): List<Dataset> = when (form.type) {
        is FormType.TOTP -> {
            val suggestions =
                suggestionFinder.findVaultSuggestions(form, count = 1)

            suggestions.mapIndexed { index, suggestion ->
                buildSuggestionDataset(index, form, suggestion)
            }
        }

        else -> {
            val suggestions = suggestionFinder.findVaultSuggestions(form, count = 4)

            suggestions.mapIndexed { index, suggestion ->
                buildSuggestionDataset(index, form, suggestion)
            } + listOf(buildAppDataset(form))
        }
    }

    private fun buildAppDataset(form: Form): Dataset {
        val remoteViews = menuDatasetBuilder.buildMenuSuggestion(
            title = context.getString(R.string.app_name),
            subtitle = context.getString(R.string.autofill_service),
            icon = R.mipmap.ic_launcher_round
        )

        return datasetBuilder.buildDataset(
            remoteViews = remoteViews,
            intentSender = context.getSelectionPendingIntent(appRequestData(form)).intentSender,
            form = form,
        )
    }

    private fun buildSuggestionDataset(
        index: Int,
        form: Form,
        suggestion: LiteVaultItem
    ): Dataset {
        val remoteViews = menuDatasetBuilder.buildMenuSuggestion(
            title = suggestion.name,
            subtitle = suggestion.subtitle(context = context, formType = form.type),
        )

        return datasetBuilder.buildDataset(
            remoteViews = remoteViews,
            intentSender = context.getSelectionPendingIntent(
                suggestionRequestData(
                    form,
                    suggestion.vaultItemId,
                    index
                )
            ).intentSender,
            form = form,
        )
    }
}