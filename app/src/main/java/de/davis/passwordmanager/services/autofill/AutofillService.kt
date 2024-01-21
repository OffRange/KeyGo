package de.davis.passwordmanager.services.autofill

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.XmlResourceParser
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.core.os.bundleOf
import de.davis.passwordmanager.R
import de.davis.passwordmanager.database.SecureElementManager
import de.davis.passwordmanager.services.autofill.builder.DatasetBuilder
import de.davis.passwordmanager.services.autofill.builder.applySaveInfo
import de.davis.passwordmanager.services.autofill.entities.TraverseNode
import de.davis.passwordmanager.services.autofill.extensions.get
import de.davis.passwordmanager.services.autofill.extensions.getPackageName
import de.davis.passwordmanager.services.autofill.extensions.getSavePendingIntent
import de.davis.passwordmanager.services.autofill.extensions.getWindowNodes
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser

@RequiresApi(Build.VERSION_CODES.O)
class AutofillService : AutofillService() {

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val windowNode = request.fillContexts.getWindowNodes().lastOrNull()
        if (windowNode == null) {
            callback.onSuccess(null)
            return
        }

        val packageName = windowNode.getPackageName()
        if (packageName == this.packageName) {
            callback.onSuccess(null)
            return
        }

        val nodeTraverse =
            NodeTraverse(request.flags).apply {
                traverseNode(TraverseNode(windowNode.rootViewNode))
            }
        if (nodeTraverse.autofillForm.autofillFields.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        val handler = CoroutineExceptionHandler { _, _ ->
            callback.onSuccess(null)
        }

        val job = CoroutineScope(Dispatchers.IO).launch(handler) {
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

                applySaveInfo(
                    nodeTraverse.autofillForm,
                    applicationContext.getBrowsers().contains(packageName),
                    request.clientState ?: bundleOf(),
                    request.id
                )
            }.build()


            callback.onSuccess(response)
        }

        cancellationSignal.setOnCancelListener {
            job.cancel()
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val saveForm = request.clientState?.get() ?: return

        val element = saveForm.getElement(request, packageManager)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            callback.onSuccess(
                getSavePendingIntent(
                    REQUEST_CODE_CREATE_ELEMENT,
                    element
                ).intentSender
            )
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                SecureElementManager.insertElement(element)
            }
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

    private fun Context.getBrowsers(): List<String> {
        val packageNames = ArrayList<String>()
        val parser: XmlResourceParser = resources.getXml(R.xml.autofill_configuration)

        runCatching {
            parser.use {
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "compatibility-package") {
                        val packageName = parser.getAttributeValue(
                            "http://schemas.android.com/apk/res/android",
                            "name"
                        )
                        packageNames.add(packageName)
                    }
                    eventType = parser.next()
                }
            }
        }

        return packageNames
    }

    companion object {
        private const val REQUEST_CODE_CREATE_ELEMENT = 1
    }
}