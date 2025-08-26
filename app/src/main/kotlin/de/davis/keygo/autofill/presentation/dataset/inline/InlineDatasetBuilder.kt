package de.davis.keygo.autofill.presentation.dataset.inline

import android.content.Context
import android.graphics.BlendMode
import android.graphics.drawable.Icon
import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.InlinePresentation
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import de.davis.keygo.R
import de.davis.keygo.autofill.presentation.dataset.DatasetBuilder
import de.davis.keygo.autofill.presentation.dataset.SuggestionFinder
import de.davis.keygo.autofill.presentation.getOnLongClickPendingIntent
import de.davis.keygo.autofill.presentation.getSelectionPendingIntent
import de.davis.keygo.autofill.presentation.model.AutofillIntentData
import de.davis.keygo.autofill.presentation.model.Extraction
import de.davis.keygo.autofill.presentation.model.appIntentData
import de.davis.keygo.autofill.presentation.model.pinnedIntentData
import de.davis.keygo.autofill.presentation.model.suggestionIntentData
import de.davis.keygo.core.domain.model.Password
import org.koin.core.annotation.Single

@Single
internal class InlineDatasetBuilder(
    private val inlineSuggestionFactory: InlineSuggestionFactory,
    private val datasetBuilder: DatasetBuilder,
    private val suggestionFinder: SuggestionFinder,
    private val context: Context,
) {

    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun buildInlineDatasets(
        specs: List<InlinePresentationSpec>,
        extraction: Extraction
    ): List<Dataset> = when (specs.size) {
        0 -> emptyList()
        1 -> listOf(buildPinnedInlineSuggestionDataset(specs.first(), extraction))
        else -> {
            val suggestions =
                suggestionFinder.findPasswordsSuggestions(extraction, count = specs.size - 2)

            suggestions.mapIndexed { index, suggestion ->
                buildInlineSuggestionDataset(
                    spec = specs[index],
                    index = index,
                    extraction = extraction,
                    suggestion = suggestion
                )
            } + listOf(
                buildAppInlineSuggestionDataset(
                    spec = specs.dropLast(1).last(),
                    extraction = extraction,
                ),
                buildPinnedInlineSuggestionDataset(spec = specs.last(), extraction = extraction)
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun buildPinnedInlineSuggestionDataset(
        spec: InlinePresentationSpec,
        extraction: Extraction
    ): Dataset {
        val presentation = inlineSuggestionFactory.buildPinnedPresentation(
            spec = spec,
            pendingIntent = context.getOnLongClickPendingIntent(),
            icon = appIcon()
        )

        return presentation.buildDataset(pinnedIntentData(extraction))
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun buildAppInlineSuggestionDataset(
        spec: InlinePresentationSpec,
        extraction: Extraction
    ): Dataset {
        val presentation = inlineSuggestionFactory.buildPresentation(
            spec = spec,
            pendingIntent = context.getOnLongClickPendingIntent(),
            icon = appIcon(),
            title = context.getString(R.string.app_name)
        )

        return presentation.buildDataset(appIntentData(extraction))
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun buildInlineSuggestionDataset(
        spec: InlinePresentationSpec,
        index: Int,
        extraction: Extraction,
        suggestion: Password
    ): Dataset {
        val presentation = inlineSuggestionFactory.buildPresentation(
            spec = spec,
            pendingIntent = context.getOnLongClickPendingIntent(),
            title = suggestion.name,
            subtitle = suggestion.username ?: "----",
        )

        return presentation.buildDataset(
            suggestionIntentData(
                extraction,
                suggestion.vaultItemId,
                index
            )
        )
    }

    private fun InlinePresentation.buildDataset(autofillIntentData: AutofillIntentData) =
        datasetBuilder.buildDataset(
            inlinePresentation = this,
            intentSender = context.getSelectionPendingIntent(autofillIntentData).intentSender,
            extraction = autofillIntentData.extraction
        )

    private fun appIcon(): Icon? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            Icon.createWithResource(context, R.mipmap.ic_launcher_round).apply {
                setTintBlendMode(BlendMode.DST)
            }
        else null
}