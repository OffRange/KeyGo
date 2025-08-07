package de.davis.keygo.autofill.presentation

import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject


class KeyGoAutofillService : AutofillService() {

    private val extractor by inject<Extractor>()

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



            Log.d(TAG, "Extracted fields: $extraction")
            callback.onSuccess(null)
        }

        cancellationSignal.setOnCancelListener {
            job.cancel()
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onFailure("[Saving] Not supported yet.")
    }

    companion object {
        private const val TAG = "KeyGoAutofillService"
    }
}

