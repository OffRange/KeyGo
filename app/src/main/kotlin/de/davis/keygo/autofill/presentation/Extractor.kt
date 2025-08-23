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
import de.davis.keygo.autofill.presentation.model.ExtractedField
import de.davis.keygo.autofill.presentation.model.Extraction
import de.davis.keygo.autofill.presentation.model.FieldType
import org.koin.core.annotation.Single

@Single
internal class Extractor() {

    fun extractRelevant(
        node: AssistStructure.ViewNode,
        manualRequest: Boolean
    ): Extraction {
        val result = mutableListOf<ExtractedField>()
        val urls = mutableSetOf<String>()
        traverse(node, manualRequest, urls, result)

        return Extraction(urls = urls, fields = result)
    }

    private fun traverse(
        node: AssistStructure.ViewNode,
        manualRequest: Boolean,
        outUrls: MutableSet<String>,
        outFields: MutableList<ExtractedField>
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

            outFields += ExtractedField(
                autofillId = node.autofillId!!,
                features = features,
                type = type,
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