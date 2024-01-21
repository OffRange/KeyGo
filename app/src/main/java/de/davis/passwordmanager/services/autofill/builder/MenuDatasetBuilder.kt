package de.davis.passwordmanager.services.autofill.builder

import android.content.Context
import android.os.Build
import android.service.autofill.Dataset
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import de.davis.passwordmanager.R
import de.davis.passwordmanager.database.ElementType
import de.davis.passwordmanager.services.autofill.builder.SuggestedDatasetBuilder.getNSuggestions
import de.davis.passwordmanager.services.autofill.entities.AutofillForm
import de.davis.passwordmanager.services.autofill.extensions.getSelectionPendingIntent

@RequiresApi(Build.VERSION_CODES.O)
class MenuDatasetBuilder(val context: Context) {

    private fun createBasicDataset(
        autofillForm: AutofillForm,
        textProvider: TextProvider,
        @DrawableRes icon: Int? = null,
        requestCode: Int
    ): Dataset = DatasetBuilder.buildDataset(
        DatasetBuilder.BuilderVariant.FieldProperties(
            autofillForm,
            context.getSelectionPendingIntent(
                requestCode,
                autofillForm,
                textProvider.element
            ).intentSender,
            remoteViews = RemoteViews(context.packageName, R.layout.layout_autofill_menu).apply {
                setTextViewText(R.id.title, textProvider.title)
                setTextViewText(R.id.text, textProvider.subtitle)

                if (icon != null)
                    setImageViewResource(R.id.imageView, icon)
            }
        )
    )

    fun createAppDataset(autofillForm: AutofillForm) = createBasicDataset(
        autofillForm = autofillForm,
        textProvider = TextProvider(
            title = context.getString(R.string.app_name),
            subtitle = context.getString(R.string.autofill_service)
        ),
        icon = R.mipmap.ic_launcher_round,
        requestCode = REQUEST_CODE_OPEN_MENU
    )

    suspend fun createSuggestedMenuDatasets(
        autofillForm: AutofillForm
    ): List<Dataset> =
        autofillForm.getNSuggestions(4, ElementType.PASSWORD.typeId) { textProvider, requestCode ->
            createBasicDataset(
                autofillForm,
                textProvider,
                icon = R.drawable.ic_baseline_password_24,
                requestCode = requestCode
            )
        }
}