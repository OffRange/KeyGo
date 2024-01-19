package de.davis.passwordmanager.services.autofill.builder

import android.content.Context
import android.content.IntentSender
import android.graphics.BlendMode
import android.graphics.drawable.Icon
import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.Field
import android.service.autofill.InlinePresentation
import android.service.autofill.Presentations
import android.view.autofill.AutofillValue
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.RemoteViews
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.DeprecatedSinceApi
import androidx.annotation.RequiresApi
import de.davis.passwordmanager.PasswordManagerApplication
import de.davis.passwordmanager.R
import de.davis.passwordmanager.services.autofill.entities.AutofillForm
import de.davis.passwordmanager.services.autofill.entities.AutofillPair
import de.davis.passwordmanager.services.autofill.extensions.getOnLongClickPendingIntent
import de.davis.passwordmanager.services.autofill.extensions.getOpenAppPendingIntent

private const val REQUEST_CODE_OPEN_INLINE = 1
private const val REQUEST_CODE_OPEN_MENU = 3
private const val REQUEST_CODE_OPEN_PINNED = 2

object DatasetBuilder {

    @RequiresApi(Build.VERSION_CODES.O)
    @DeprecatedSinceApi(Build.VERSION_CODES.R)
    fun createMenuDatasets(
        autofillForm: AutofillForm,
        context: Context
    ): List<Dataset> {
        return listOf(
            /*TODO add support for suggested items*/
            buildDataset(
                BuilderVariant.FieldProperties(
                    autofillForm,
                    context.getOpenAppPendingIntent(
                        REQUEST_CODE_OPEN_MENU,
                        autofillForm
                    ).intentSender,
                    remoteViews = RemoteViews(
                        context.packageName,
                        android.R.layout.simple_list_item_1
                    ).apply {
                        setTextViewText(android.R.id.text1, context.getText(R.string.app_name))
                    }
                )
            )
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun createInlineDatasets(
        inlineSuggestionsRequest: InlineSuggestionsRequest,
        autofillForm: AutofillForm,
        context: Context
    ): List<Dataset> {
        val specs = inlineSuggestionsRequest.inlinePresentationSpecs

        return InlineDatasetBuilder(context).run {
            when (specs.size) {
                0,
                1 -> listOf(createPinnedAppDataset(autofillForm, specs.last()))

                else -> {
                    /*TODO add support for suggested items*/
                    listOf(
                        createBasicAppDataset(autofillForm, specs.dropLast(1).last()),
                        createPinnedAppDataset(autofillForm, specs.last())
                    )
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun buildDataset(builderVariant: BuilderVariant): Dataset =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Builder.BuilderApi33Impl
        } else {
            Builder.BuilderApi26Impl
        }.buildDataset(builderVariant)


    sealed interface BuilderVariant {
        data class FieldProperties(
            val autofillForm: AutofillForm,
            val intentSender: IntentSender,
            val inlinePresentation: InlinePresentation? = null,
            val remoteViews: RemoteViews? = null
        ) : BuilderVariant {
            init {
                if (inlinePresentation == null && remoteViews == null)
                    throw IllegalArgumentException("InlinePresentation or RemoteViews must be set")
            }
        }

        data class FillProperties(val autofillPairs: List<AutofillPair>) : BuilderVariant {

            companion object {
                internal val DUMMY_REMOTE_VIEWS =
                    RemoteViews(
                        PasswordManagerApplication.getAppContext().packageName,
                        android.R.layout.simple_list_item_1
                    )
            }
        }
    }

}

private sealed interface Builder {
    fun buildDataset(builderProp: DatasetBuilder.BuilderVariant): Dataset
    fun Dataset.Builder.applyAutofillForm(autofillForm: AutofillForm)

    fun Dataset.Builder.applyAutofillPairs(autofillPairs: List<AutofillPair>)


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    data object BuilderApi33Impl : Builder {

        override fun buildDataset(builderProp: DatasetBuilder.BuilderVariant): Dataset =
            when (builderProp) {
                is DatasetBuilder.BuilderVariant.FieldProperties -> {
                    Dataset.Builder(
                        Presentations.Builder().apply {
                            builderProp.inlinePresentation?.let { setInlinePresentation(it) }
                            builderProp.remoteViews?.let { setMenuPresentation(it) }
                        }.build()
                    ).apply {
                        setAuthentication(builderProp.intentSender)
                        applyAutofillForm(builderProp.autofillForm)
                    }
                }

                is DatasetBuilder.BuilderVariant.FillProperties -> {
                    Dataset.Builder(
                        Presentations.Builder()
                            .setMenuPresentation(DatasetBuilder.BuilderVariant.FillProperties.DUMMY_REMOTE_VIEWS)
                            .build()
                    ).apply {
                        applyAutofillPairs(builderProp.autofillPairs)
                    }
                }
            }.build()

        override fun Dataset.Builder.applyAutofillForm(autofillForm: AutofillForm) {
            autofillForm.autofillFields.forEach {
                setField(
                    it.autofillId,
                    Field.Builder().build()
                )
            }
        }

        override fun Dataset.Builder.applyAutofillPairs(autofillPairs: List<AutofillPair>) {
            autofillPairs.forEach {
                setField(
                    it.autofillField.autofillId,
                    Field.Builder().setValue(AutofillValue.forText(it.data)).build()
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    @RequiresApi(Build.VERSION_CODES.O)
    data object BuilderApi26Impl : Builder {
        override fun buildDataset(builderProp: DatasetBuilder.BuilderVariant): Dataset =
            when (builderProp) {
                is DatasetBuilder.BuilderVariant.FieldProperties -> {
                    val datasetBuilder = builderProp.remoteViews?.let {
                        Dataset.Builder(it)
                    } ?: Dataset.Builder()

                    datasetBuilder.apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            builderProp.inlinePresentation?.let { setInlinePresentation(it) }
                        }

                        setAuthentication(builderProp.intentSender)

                        applyAutofillForm(builderProp.autofillForm)
                    }
                }

                is DatasetBuilder.BuilderVariant.FillProperties -> {
                    Dataset.Builder().apply {
                        applyAutofillPairs(builderProp.autofillPairs)
                    }
                }
            }.build()

        override fun Dataset.Builder.applyAutofillForm(autofillForm: AutofillForm) {
            autofillForm.autofillFields.forEach {
                setValue(
                    it.autofillId,
                    null
                )
            }
        }

        override fun Dataset.Builder.applyAutofillPairs(autofillPairs: List<AutofillPair>) {
            autofillPairs.forEach {
                setValue(
                    it.autofillField.autofillId,
                    AutofillValue.forText(it.data),
                    DatasetBuilder.BuilderVariant.FillProperties.DUMMY_REMOTE_VIEWS
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
private class InlineDatasetBuilder(private val context: Context) {

    fun createBasicAppDataset(
        autofillForm: AutofillForm,
        inlinePresentationSpec: InlinePresentationSpec
    ) = DatasetBuilder.buildDataset(
        DatasetBuilder.BuilderVariant.FieldProperties(
            autofillForm,
            context.getOpenAppPendingIntent(
                REQUEST_CODE_OPEN_PINNED,
                autofillForm
            ).intentSender,
            inlinePresentation = InlinePresentationBuilder.createBasicPresentation(
                context.getString(R.string.app_name),
                context.getString(R.string.autofill_service),
                R.mipmap.ic_launcher_round.toDrawableIcon(),
                inlinePresentationSpec,
                context.getOnLongClickPendingIntent()
            )
        )
    )

    fun createPinnedAppDataset(
        autofillForm: AutofillForm,
        inlinePresentationSpec: InlinePresentationSpec
    ) = DatasetBuilder.buildDataset(
        DatasetBuilder.BuilderVariant.FieldProperties(
            autofillForm,
            context.getOpenAppPendingIntent(
                REQUEST_CODE_OPEN_INLINE,
                autofillForm
            ).intentSender,
            inlinePresentation = InlinePresentationBuilder.createPinnedPresentation(
                R.mipmap.ic_launcher_round.toDrawableIcon(),
                inlinePresentationSpec,
                context.getOnLongClickPendingIntent()
            )
        )
    )


    private fun Int.toDrawableIcon(): Icon =
        Icon.createWithResource(context, this).apply {
            setTintBlendMode(BlendMode.DST)
        }
}