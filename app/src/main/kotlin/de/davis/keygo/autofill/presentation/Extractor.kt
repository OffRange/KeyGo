package de.davis.keygo.autofill.presentation

import android.app.assist.AssistStructure
import android.os.Build
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

@Single
internal class Extractor() {

    /**
     * Extracts relevant form fields from the provided [AssistStructure.ViewNode].
     * It traverses the view hierarchy, identifying input fields that are important for autofill.
     *
     * @param node The root view node of the assist structure to extract from.
     * @param manualRequest Indicates if the extraction is triggered by a manual user request.
     * @return A [Form] containing the extracted fields and associated URLs, or null if no focused
     * field is found or the focused field could not be classified.
     */
    fun extractRelevant(
        node: AssistStructure.ViewNode,
        manualRequest: Boolean
    ): Form? {
        val result = mutableListOf<FormField>()
        val urls = mutableSetOf<String>()
        traverse(node, manualRequest, urls, result)

        // We filter out the fields that are not in the same group as the focused field
        // This forces us to only fill one type at a time (e.g. credentials or credit cards).
        Log.d(TAG, "Extracted fields: $result")
        val focusedFieldType = result.firstOrNull { it.focused }?.type ?: return null
        if (focusedFieldType is FieldType.Undefined) return null

        return Form(
            urls = urls,
            fields = result.filter { it.type.group == focusedFieldType.group },
            type = focusedFieldType.toFormType()
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