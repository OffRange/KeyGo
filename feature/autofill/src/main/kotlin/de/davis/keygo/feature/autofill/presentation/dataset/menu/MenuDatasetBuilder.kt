package de.davis.keygo.feature.autofill.presentation.dataset.menu

import android.content.Context
import android.os.Build
import android.service.autofill.Dataset
import android.util.Log
import androidx.annotation.DeprecatedSinceApi
import de.davis.keygo.core.item.domain.model.lite.LiteVaultItem
import de.davis.keygo.feature.autofill.R
import de.davis.keygo.feature.autofill.domain.repository.SmsCodeRepository
import de.davis.keygo.feature.autofill.presentation.dataset.DatasetBuilder
import de.davis.keygo.feature.autofill.presentation.dataset.SuggestionFinder
import de.davis.keygo.feature.autofill.presentation.getSelectionPendingIntent
import de.davis.keygo.feature.autofill.presentation.model.FieldType
import de.davis.keygo.feature.autofill.presentation.model.Form
import de.davis.keygo.feature.autofill.presentation.model.FormType
import de.davis.keygo.feature.autofill.presentation.model.appRequestData
import de.davis.keygo.feature.autofill.presentation.model.generatePasswordRequestData
import de.davis.keygo.feature.autofill.presentation.model.smsOtpRequestData
import de.davis.keygo.feature.autofill.presentation.model.suggestionRequestData
import de.davis.keygo.feature.autofill.presentation.subtitle
import org.koin.core.annotation.Single
import de.davis.keygo.core.ui.R as CoreUiR

@Single
@DeprecatedSinceApi(Build.VERSION_CODES.R)
internal class MenuDatasetBuilder(
    private val suggestionFinder: SuggestionFinder,
    private val menuDatasetBuilder: MenuSuggestionFactory,
    private val datasetBuilder: DatasetBuilder,
    private val smsCodeRepository: SmsCodeRepository,
    private val context: Context
) {

    suspend fun buildMenuDatasets(
        targetPackage: String,
        form: Form
    ): List<Dataset> = when (form.type) {
        is FormType.TOTP -> {
            val suggestions = suggestionFinder.findVaultSuggestions(form, count = 1)

            val canOfferSmsOtp = smsCodeRepository.canOfferSuggestion(targetPackage)
            Log.d(TAG, "canOfferSmsOtp: $canOfferSmsOtp")

            buildList {
                addAll(
                    suggestions.mapIndexed { index, suggestion ->
                        buildSuggestionDataset(
                            index = index,
                            form = form,
                            suggestion = suggestion
                        )
                    }
                )

                if (canOfferSmsOtp) add(buildSmsOtpDataset(form = form))
            }
        }

        else -> {
            val suggestions = suggestionFinder.findVaultSuggestions(form, count = 4)

            suggestions.mapIndexed { index, suggestion ->
                buildSuggestionDataset(index, form, suggestion)
            } + listOfNotNull(buildGeneratePasswordDataset(form), buildAppDataset(form))
        }
    }

    private fun buildGeneratePasswordDataset(form: Form): Dataset? {
        if (form.fields.none { it.type is FieldType.Credentials.Password }) return null

        val remoteViews = menuDatasetBuilder.buildMenuSuggestion(
            title = context.getString(de.davis.keygo.feature.item.create.R.string.generate_password),
            icon = R.drawable.baseline_auto_awesome_24
        )

        val requestData = generatePasswordRequestData(form)
        return datasetBuilder.buildDataset(
            remoteViews = remoteViews,
            intentSender = context.getSelectionPendingIntent(requestData).intentSender,
            form = requestData.form,
        )
    }

    private fun buildAppDataset(form: Form): Dataset {
        val remoteViews = menuDatasetBuilder.buildMenuSuggestion(
            title = context.getString(R.string.open_app),
            subtitle = context.getString(R.string.autofill_service),
            icon = CoreUiR.mipmap.ic_launcher_round
        )

        return datasetBuilder.buildDataset(
            remoteViews = remoteViews,
            intentSender = context.getSelectionPendingIntent(appRequestData(form)).intentSender,
            form = form,
        )
    }

    private fun buildSmsOtpDataset(form: Form): Dataset {
        val remoteViews = menuDatasetBuilder.buildMenuSuggestion(
            title = context.getString(R.string.sms_code),
            subtitle = context.getString(R.string.autofill_service),
            icon = R.drawable.outline_sms_24
        )

        return datasetBuilder.buildDataset(
            remoteViews = remoteViews,
            intentSender = context.getSelectionPendingIntent(smsOtpRequestData(form)).intentSender,
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
                    suggestion.id,
                    index
                )
            ).intentSender,
            form = form,
        )
    }

    private companion object {
        const val TAG = "MenuDatasetBuilder"
    }
}