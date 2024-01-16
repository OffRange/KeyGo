package de.davis.passwordmanager.services.autofill

import android.annotation.SuppressLint
import android.app.assist.AssistStructure.WindowNode
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillContext
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import de.davis.passwordmanager.services.autofill.builder.DatasetBuilder

@RequiresApi(Build.VERSION_CODES.O)
class AutofillService : AutofillService() {

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val windowNode = getWindowNodes(request.fillContexts).lastOrNull()
        if (windowNode == null) {
            callback.onSuccess(null)
            return
        }

        if (windowNode.title.split("/").first() == packageName) {
            callback.onSuccess(null)
            return
        }

        val nodeTraverse =
            NodeTraverse(request.flags).apply {
                traverseNode(windowNode.rootViewNode)
            }
        if (nodeTraverse.autofillForm.autofillFields.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        @SuppressLint("DeprecatedSinceApi")
        val response = FillResponse.Builder().apply {
            if (hasSupportForInlineSuggestions(request)) {
                request.inlineSuggestionsRequest?.let {
                    DatasetBuilder.createInlineDatasets(
                        it,
                        nodeTraverse.autofillForm,
                        applicationContext
                    )
                } ?: emptyList()
            } else {
                DatasetBuilder.createMenuDatasets(nodeTraverse.autofillForm, applicationContext)
            }.forEach { addDataset(it) }
        }.build()

        callback.onSuccess(response)
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        TODO("Not yet implemented")
    }

    private fun getWindowNodes(fillContexts: List<FillContext>): List<WindowNode> {
        val fillContext = fillContexts.lastOrNull() ?: return emptyList()
        return (0 until fillContext.structure.windowNodeCount).map {
            fillContext.structure.getWindowNodeAt(it)
        }
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    private fun hasSupportForInlineSuggestions(request: FillRequest): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            request.inlineSuggestionsRequest?.let {
                val maxSuggestion = it.maxSuggestionCount
                val specCount = it.inlinePresentationSpecs.count()
                maxSuggestion > 0 && specCount > 0
            } ?: false
        } else {
            false
        }
}