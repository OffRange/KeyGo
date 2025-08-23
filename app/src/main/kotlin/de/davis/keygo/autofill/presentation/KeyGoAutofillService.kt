package de.davis.keygo.autofill.presentation

import android.app.assist.AssistStructure
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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

// TODO: handle authentication settings
//  implement smart authentication --> use Digital Asset Links to verify a app - fail should warn user
class KeyGoAutofillService : AutofillService() {

    private val extractor by inject<Extractor>()
    private val datasetProvider by inject<AutofillDatasetProvider>()

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess(null)
            return
        }

        val windowNode = (0 until structure.windowNodeCount).mapNotNull {
            structure.getWindowNodeAt(it)
        }.lastOrNull()
        if (windowNode == null) {
            callback.onSuccess(null)
            return
        }

        if (windowNode.title.split("/").first() == packageName) {
            callback.onSuccess(null)
            return
        }

        val handler = CoroutineExceptionHandler { _, exception ->
            Log.w(TAG, "Error during autofill extraction", exception)
            callback.onSuccess(null)
        }

        val job = CoroutineScope(Dispatchers.IO + handler).launch {
            val extraction =
                extractor.extractRelevant(windowNode.rootViewNode, manualRequest = false)

            if (!extraction.hasFields()) {
                callback.onSuccess(null)
                return@launch
            }

            val inCompatibilityMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                (request.flags and FillRequest.FLAG_COMPATIBILITY_MODE_REQUEST) != 0
            else true
            Log.d(TAG, "In Compatibility Mode: $inCompatibilityMode")
            Log.d(TAG, "Extracted fields [${extraction.fields.size}]: $extraction")

            val dataset = datasetProvider.getAutofillDatasets(request, extraction)
            val response = FillResponse.Builder().apply {
                dataset.forEach(::addDataset)
                applySaveInfo(
                    extraction = extraction,
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
        Log.d(TAG, "onSaveRequest called with request: ${request.fillContexts}")
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onFailure("No structure found")
            return
        }

        val clientState = request.clientState
        if (clientState == null) {
            callback.onFailure("No client state found")
            return
        }

        // TODO show UI

        callback.onSuccess()
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

