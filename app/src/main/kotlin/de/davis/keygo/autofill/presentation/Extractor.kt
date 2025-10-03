package de.davis.keygo.autofill.presentation

import android.app.assist.AssistStructure
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.service.autofill.AutofillService.SERVICE_META_DATA
import android.util.Log
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.MultiAutoCompleteTextView
import android.widget.TextView
import de.davis.keygo.autofill.presentation.mapper.toFieldFeatures
import de.davis.keygo.autofill.presentation.mapper.toFormType
import de.davis.keygo.autofill.presentation.model.FieldType
import de.davis.keygo.autofill.presentation.model.Form
import de.davis.keygo.autofill.presentation.model.FormField
import org.koin.core.annotation.Single
import org.xmlpull.v1.XmlPullParser

@Single
internal class Extractor(
    private val applicationContext: Context
) {

    private val browsers by lazy {
        val service = applicationContext.packageManager.getServiceInfo(
            ComponentName(applicationContext, KeyGoAutofillService::class.java),
            PackageManager.GET_META_DATA
        )

        val parser = service.loadXmlMetaData(applicationContext.packageManager, SERVICE_META_DATA)
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

    /**
     * Extracts relevant form fields from the provided [AssistStructure.ViewNode].
     * It traverses the view hierarchy, identifying input fields that are important for autofill.
     *
     * @param windowNode The window node of the assist structure to extract from.
     * @param manualRequest Indicates if the extraction is triggered by a manual user request.
     * @return A [Form] containing the extracted fields and associated URLs, or null if no focused
     * field is found or the focused field could not be classified.
     */
    fun extractRelevant(
        windowNode: AssistStructure.WindowNode,
        manualRequest: Boolean
    ): Form? {
        val result = mutableListOf<FormField>()
        val urls = mutableSetOf<String>()
        traverse(windowNode.rootViewNode, manualRequest, urls, result)

        // We filter out the fields that are not in the same group as the focused field
        // This forces us to only fill one type at a time (e.g. credentials or credit cards).
        Log.d(TAG, "Extracted fields: $result")
        val focusedFieldType = result.firstOrNull { it.focused }?.type
            ?: result.firstOrNull()?.type
            ?: return null
        if (focusedFieldType is FieldType.Undefined) return null

        val windowPackageName = windowNode.packageName
        val isBrowser = windowPackageName in browsers
        val isSuspicious = !isBrowser && urls.isNotEmpty()

        if (isSuspicious)
            Log.w(
                TAG,
                "Window package name $windowPackageName is not a browser but has web URLs: $urls"
            )

        return Form(
            urls = urls,
            fields = result.filter { it.type.group == focusedFieldType.group },
            type = focusedFieldType.toFormType(),
            isBrowser = isBrowser,
            appPackageName = windowPackageName,
            isSuspicious = isSuspicious
        )
    }

    private fun traverse(
        node: AssistStructure.ViewNode,
        manualRequest: Boolean,
        outUrls: MutableSet<String>,
        outFields: MutableList<FormField>
    ) {
        node.getUrl()?.let {
            outUrls += it
        }

        if (isSignalLeaf(node)) {
            if (node.autofillId == null)
                return

            val isImportant = node.isImportantForAutofill() || manualRequest
            if (!isImportant) {
                Log.d(
                    TAG,
                    "Skipping node [not important]: ${node.className} - HTML-Tag: ${node.htmlInfo?.tag}"
                )
                return
            }

            if (!node.looksLikeInput()) {
                Log.d(
                    TAG,
                    "Skipping node [not input]: ${node.className} - HTML-Tag: ${node.htmlInfo?.tag}"
                )
                return
            }

            val features = node.toFieldFeatures()
            val type = Classifier.classify(features)
            if (type is FieldType.Undefined) {
                Log.d(
                    TAG,
                    "Skipping node [undefined type]: ${node.className} with $features"
                )
                return
            }

            outFields += FormField(
                autofillId = node.autofillId!!,
                type = type,
                focused = node.isFocused
            )
            return
        }

        (0 until node.childCount).forEach {
            traverse(node.getChildAt(it), manualRequest, outUrls, outFields)
        }
    }

    private fun isSignalLeaf(node: AssistStructure.ViewNode): Boolean {
        val noChildren = node.childCount == 0
        val onlyCosmeticChild = node.childCount == 1
                && node.getChildAt(0).className == TextView::class.qualifiedName
                && node.getChildAt(0).childCount == 0
                && node.getChildAt(0).text.isNullOrBlank()
                && node.getChildAt(0).autofillHints?.all { it.isNullOrBlank() } == true

        return noChildren || onlyCosmeticChild
    }

    private fun AssistStructure.ViewNode.isImportantForAutofill(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            when (importantForAutofill) {
                View.IMPORTANT_FOR_AUTOFILL_AUTO,
                View.IMPORTANT_FOR_AUTOFILL_YES,
                View.IMPORTANT_FOR_AUTOFILL_YES_EXCLUDE_DESCENDANTS -> true

                else -> false
            }
        else true

    private fun AssistStructure.ViewNode.looksLikeInput(): Boolean {
        // HTML signals (native browsers / WebView with HtmlInfo)
        val htmlTag = htmlInfo?.tag?.lowercase()
        val isHtmlInput = htmlTag == "input" || htmlTag == "textarea"

        // Android widget signals
        val isEditableView = isEditableView()
        return isHtmlInput || isEditableView
    }

    private fun AssistStructure.ViewNode.isEditableView(): Boolean = isEnabled && when (className) {
        EditText::class.qualifiedName,
        MultiAutoCompleteTextView::class.qualifiedName,
        AutoCompleteTextView::class.qualifiedName -> true

        else -> false
    }

    private fun AssistStructure.ViewNode.getUrl() = webDomain
        ?.takeIf { it.isNotBlank() }
        ?.let {
            val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) webScheme ?: "https"
            else "https"
            "$scheme://$it"
        }

    companion object {
        private const val TAG = "Extractor"
    }
}