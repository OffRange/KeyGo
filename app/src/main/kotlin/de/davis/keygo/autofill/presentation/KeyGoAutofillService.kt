package de.davis.keygo.autofill.presentation

import android.app.assist.AssistStructure
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.util.Log
import android.view.autofill.AutofillId
import androidx.core.os.bundleOf
import de.davis.keygo.autofill.presentation.dataset.applySaveInfo
import de.davis.keygo.autofill.presentation.dataset.getForm
import de.davis.keygo.autofill.presentation.model.Form
import de.davis.keygo.autofill.presentation.model.FormField
import de.davis.keygo.autofill.presentation.model.SaveRequestData
import de.davis.keygo.core.domain.usecase.HasValidAccessUseCase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.xmlpull.v1.XmlPullParser

// TODO: handle authentication settings
//  implement smart authentication --> use Digital Asset Links to verify a app - fail should warn user
class KeyGoAutofillService : AutofillService() {

    private val extractor by inject<Extractor>()
    private val datasetProvider by inject<AutofillDatasetProvider>()
    private val hasValidAccess by inject<HasValidAccessUseCase>()

    private val browsers by lazy {
        val service = packageManager.getServiceInfo(
            ComponentName(this, javaClass),
            PackageManager.GET_META_DATA
        )

        val parser = service.loadXmlMetaData(packageManager, SERVICE_META_DATA)
            ?: return@lazy emptySet<String>()

        parser.use {
            var type = it.eventType
            buildSet {
                while (type != XmlPullParser.END_DOCUMENT) {
                    if (type != XmlPullParser.START_TAG || it.name != "compatibility-package") {
                        type = it.next()
                        continue
                    }

                    val packageName = it.getAttributeValue(
                        "http://schemas.android.com/apk/res/android",
                        "name"
                    )
                    add(packageName)
                    type = it.next()
                }
            }
        }
    }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val handler = CoroutineExceptionHandler { _, exception ->
            Log.w(TAG, "Error during autofill extraction", exception)
            callback.onSuccess(null)
        }

        val job = CoroutineScope(Dispatchers.IO + handler).launch {
            if (!hasValidAccess()) {
                Log.w(TAG, "No valid access - not filling")
                callback.onSuccess(null)
                return@launch
            }

            val structure = request.fillContexts.lastOrNull()?.structure
            if (structure == null) {
                callback.onSuccess(null)
                return@launch
            }

            val windowNode = (0 until structure.windowNodeCount).mapNotNull {
                structure.getWindowNodeAt(it)
            }.lastOrNull()
            if (windowNode == null) {
                callback.onSuccess(null)
                return@launch
            }

            if (windowNode.packageName == packageName) {
                callback.onSuccess(null)
                return@launch
            }

            val isManual = request.flags and FillRequest.FLAG_MANUAL_REQUEST != 0
            val form = extractor.extractRelevant(windowNode, manualRequest = isManual)
                ?: run {
                    Log.w(TAG, "Could not extract form")
                    callback.onSuccess(null)
                    return@launch
                }

            if (!form.hasFields()) {
                callback.onSuccess(null)
                return@launch
            }

            val inCompatibilityMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                (request.flags and FillRequest.FLAG_COMPATIBILITY_MODE_REQUEST) != 0
            else true
            Log.d(TAG, "In Compatibility Mode: $inCompatibilityMode")
            Log.d(TAG, "Extracted form: $form")

            val dataset = datasetProvider.getAutofillDatasets(request, form)
            val response = FillResponse.Builder().apply {
                dataset.forEach(::addDataset)
                applySaveInfo(
                    form = form,
                    clientInfo = request.clientState ?: bundleOf(),
                    requestId = request.id,
                    inCompatibilityMode = inCompatibilityMode
                )
            }.build()
            callback.onSuccess(response)
        }

        cancellationSignal.setOnCancelListener {
            job.cancel()
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        Log.d(TAG, "Save request received: $request")
        val clientState = request.clientState
        if (clientState == null) {
            callback.onFailure("No client state found")
            return
        }

        val form = clientState.getForm()
            ?.satisfyFields(request) // Fill the form fields with values from the request
            ?: run {
                callback.onFailure("No form in client state")
                return
            }

        val saveRequestData = SaveRequestData(form)
        Log.d(
            TAG,
            "To be saved - $saveRequestData"
        )

        val savePendingIntent = getSavePendingIntent(saveRequestData)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            callback.onSuccess(savePendingIntent.intentSender)
            return
        }

        // For older android versions, we just start a new activity
        runCatching {
            savePendingIntent.send()
        }.onFailure {
            Log.w(TAG, "Error starting save activity", it)
            callback.onFailure("Error starting save activity")
            return
        }.onSuccess {
            callback.onSuccess()
        }
    }

    private fun Form.satisfyFields(request: SaveRequest): Form {
        val satisfiedFields = fields.mapNotNull { field ->
            val node = request.find(field) ?: return@mapNotNull null
            if (node.autofillValue == null) return@mapNotNull null
            val value = node.autofillValue?.textValue?.toString()
            field.copy(autofillValue = value)
        }
        return copy(fields = satisfiedFields)
    }

    private fun SaveRequest.find(formField: FormField): AssistStructure.ViewNode? {
        val structure = fillContexts.find { it.requestId == formField.requestId }
            ?.structure
            ?: return null
        return (0 until structure.windowNodeCount).firstNotNullOfOrNull {
            structure.getWindowNodeAt(it)
                ?.rootViewNode
                ?.findChildById(formField.autofillId)
        }
    }

    private fun AssistStructure.ViewNode.findChildById(id: AutofillId): AssistStructure.ViewNode? {
        if (autofillId == id) return this
        for (i in 0 until childCount) {
            val child = getChildAt(i).findChildById(id)
            if (child != null) return child
        }
        return null
    }

    companion object {
        private const val TAG = "KeyGoAutofillService"
    }
}

