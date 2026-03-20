package de.davis.keygo.feature.autofill.presentation.dataset.inline

import android.content.Context
import android.graphics.BlendMode
import android.graphics.drawable.Icon
import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.InlinePresentation
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import de.davis.keygo.core.item.domain.model.lite.LiteVaultItem
import de.davis.keygo.feature.autofill.presentation.dataset.DatasetBuilder
import de.davis.keygo.feature.autofill.presentation.dataset.SuggestionFinder
import de.davis.keygo.feature.autofill.presentation.getOnLongClickPendingIntent
import de.davis.keygo.feature.autofill.presentation.getSelectionPendingIntent
import de.davis.keygo.feature.autofill.presentation.model.FillRequestData
import de.davis.keygo.feature.autofill.presentation.model.Form
import de.davis.keygo.feature.autofill.presentation.model.FormType
import de.davis.keygo.feature.autofill.presentation.model.appRequestData
import de.davis.keygo.feature.autofill.presentation.model.pinnedRequestData
import de.davis.keygo.feature.autofill.presentation.model.suggestionRequestData
import de.davis.keygo.feature.autofill.presentation.subtitle
import org.koin.core.annotation.Single
import de.davis.keygo.core.ui.R as CoreUiR

@Single
internal class InlineDatasetBuilder(
    private val inlineSuggestionFactory: InlineSuggestionFactory,
    private val datasetBuilder: DatasetBuilder,
    private val suggestionFinder: SuggestionFinder,
    private val context: Context,
) {

    // TODO: include a option to generate a secure password -> make the generate UI a standalone
    //  composable like a TextField
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun buildInlineDatasets(
        specs: List<InlinePresentationSpec>,
        form: Form
    ): List<Dataset> = when (form.type) {
        is FormType.TOTP -> buildTotpInlineDataset(specs, form)
        else -> buildDefaultInlineDatasets(specs, form)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun buildTotpInlineDataset(
        specs: List<InlinePresentationSpec>,
        form: Form
    ) = when (specs.size) {
        0 -> emptyList()
        else -> {
            val suggestions =
                suggestionFinder.findVaultSuggestions(form, count = 1)

            suggestions.mapIndexed { index, suggestion ->
                buildInlineSuggestionDataset(
                    spec = specs[index],
                    index = index,
                    form = form,
                    suggestion = suggestion
                )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun buildDefaultInlineDatasets(
        specs: List<InlinePresentationSpec>,
        form: Form
    ) = when (specs.size) {
        0 -> emptyList()
        1 -> listOf(buildPinnedInlineSuggestionDataset(specs.first(), form))
        else -> {
            val suggestions =
                suggestionFinder.findVaultSuggestions(form, count = specs.size - 2)

            suggestions.mapIndexed { index, suggestion ->
                buildInlineSuggestionDataset(
                    spec = specs[index],
                    index = index,
                    form = form,
                    suggestion = suggestion
                )
            } + listOf(
                buildAppInlineSuggestionDataset(
                    spec = specs.dropLast(1).last(),
                    form = form,
                ),
                buildPinnedInlineSuggestionDataset(
                    spec = specs.last(),
                    form = form
                )
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun buildPinnedInlineSuggestionDataset(
        spec: InlinePresentationSpec,
        form: Form
    ): Dataset {
        val presentation = inlineSuggestionFactory.buildPinnedPresentation(
            spec = spec,
            pendingIntent = context.getOnLongClickPendingIntent(),
            icon = appIcon()
        )

        return presentation.buildDataset(pinnedRequestData(form))
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun buildAppInlineSuggestionDataset(
        spec: InlinePresentationSpec,
        form: Form
    ): Dataset {
        val presentation = inlineSuggestionFactory.buildPresentation(
            spec = spec,
            pendingIntent = context.getOnLongClickPendingIntent(),
            icon = appIcon(),
            title = context.getString(CoreUiR.string.app_name)
        )

        return presentation.buildDataset(appRequestData(form))
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun buildInlineSuggestionDataset(
        spec: InlinePresentationSpec,
        index: Int,
        form: Form,
        suggestion: LiteVaultItem
    ): Dataset {
        val presentation = inlineSuggestionFactory.buildPresentation(
            spec = spec,
            pendingIntent = context.getOnLongClickPendingIntent(),
            title = suggestion.name,
            subtitle = suggestion.subtitle(context = context, formType = form.type),
        )

        return presentation.buildDataset(
            suggestionRequestData(
                form,
                suggestion.vaultItemId,
                index
            )
        )
    }

    private fun InlinePresentation.buildDataset(fillRequestData: FillRequestData) =
        datasetBuilder.buildDataset(
            inlinePresentation = this,
            intentSender = context.getSelectionPendingIntent(fillRequestData).intentSender,
            form = fillRequestData.form
        )

    private fun appIcon(): Icon? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            Icon.createWithResource(context, CoreUiR.mipmap.ic_launcher_round).apply {
                setTintBlendMode(BlendMode.DST)
            }
        else null
}