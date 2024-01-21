package de.davis.passwordmanager.services.autofill.builder

import android.content.Context
import android.graphics.BlendMode
import android.graphics.drawable.Icon
import android.os.Build
import android.service.autofill.Dataset
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import de.davis.passwordmanager.R
import de.davis.passwordmanager.database.ElementType
import de.davis.passwordmanager.services.autofill.builder.SuggestedDatasetBuilder.getNSuggestions
import de.davis.passwordmanager.services.autofill.entities.AutofillForm
import de.davis.passwordmanager.services.autofill.extensions.getOnLongClickPendingIntent
import de.davis.passwordmanager.services.autofill.extensions.getSelectionPendingIntent

@RequiresApi(Build.VERSION_CODES.R)
class InlineDatasetBuilder(private val context: Context) {

    private fun createBasicDataset(
        autofillForm: AutofillForm,
        textProvider: TextProvider,
        icon: Icon? = null,
        inlinePresentationSpec: InlinePresentationSpec,
        requestCode: Int
    ): Dataset = DatasetBuilder.buildDataset(
        DatasetBuilder.BuilderVariant.FieldProperties(
            autofillForm,
            context.getSelectionPendingIntent(
                requestCode,
                autofillForm,
            ).intentSender,
            inlinePresentation = InlinePresentationBuilder.createBasicPresentation(
                title = textProvider.title,
                subtitle = textProvider.subtitle,
                icon = icon,
                inlinePresentationSpec = inlinePresentationSpec,
                pendingIntent = context.getOnLongClickPendingIntent()
            )
        )
    )

    fun createAppDataset(
        autofillForm: AutofillForm,
        inlinePresentationSpec: InlinePresentationSpec
    ) = createBasicDataset(
        autofillForm = autofillForm,
        textProvider = TextProvider(
            title = context.getString(R.string.app_name),
            subtitle = context.getString(R.string.autofill_service)
        ),
        icon = R.mipmap.ic_launcher_round.toDrawableIcon(),
        inlinePresentationSpec = inlinePresentationSpec,
        requestCode = REQUEST_CODE_OPEN_INLINE
    )

    fun createPinnedAppDataset(
        autofillForm: AutofillForm,
        inlinePresentationSpec: InlinePresentationSpec
    ) = DatasetBuilder.buildDataset(
        DatasetBuilder.BuilderVariant.FieldProperties(
            autofillForm,
            context.getSelectionPendingIntent(
                REQUEST_CODE_OPEN_PINNED,
                autofillForm
            ).intentSender,
            inlinePresentation = InlinePresentationBuilder.createPinnedPresentation(
                R.mipmap.ic_launcher_round.toDrawableIcon(),
                inlinePresentationSpec,
                context.getOnLongClickPendingIntent()
            )
        )
    )


    suspend fun createSuggestedInlineDatasets(
        autofillForm: AutofillForm,
        inlinePresentationSpec: List<InlinePresentationSpec>
    ): List<Dataset> = autofillForm.getNSuggestions(
        inlinePresentationSpec.size,
        ElementType.PASSWORD.typeId
    ) { textProvider, requestCode ->
        createBasicDataset(
            autofillForm = autofillForm,
            textProvider = textProvider,
            inlinePresentationSpec = inlinePresentationSpec[requestCode],
            requestCode = requestCode
        )
    }

    private fun Int.toDrawableIcon(): Icon =
        Icon.createWithResource(context, this).apply {
            setTintBlendMode(BlendMode.DST)
        }
}