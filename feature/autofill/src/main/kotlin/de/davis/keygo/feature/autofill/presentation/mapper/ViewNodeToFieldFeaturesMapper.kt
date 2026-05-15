package de.davis.keygo.feature.autofill.presentation.mapper

import android.app.assist.AssistStructure
import de.davis.keygo.feature.autofill.presentation.model.FieldFeatures

internal fun buildFieldFeatures(
    autofillHints: Array<String>?,
    htmlAttributes: List<Pair<String, String>>?,
    idEntry: String?,
    hint: String?,
    text: String?,
): FieldFeatures {
    val resolvedAutofillHints = autofillHints
        ?.filterNot { it.isBlank() }
        ?.toSet()
        ?: emptySet()

    val resolvedHtmlAttributes = htmlAttributes
        ?.filterNot { it.first.isBlank() || it.second.isBlank() }
        ?.associate { it.first to it.second }
        ?: emptyMap()

    val tokens = listOfNotNull(
        idEntry?.ifBlank { null },
        hint?.ifBlank { null },
        text?.ifBlank { null },
    ).map(CharSequence::toString)
        .map(String::lowercase).toSet()

    return FieldFeatures(
        autofillHints = resolvedAutofillHints,
        htmlAttributes = resolvedHtmlAttributes,
        tokens = tokens,
    )
}

internal fun AssistStructure.ViewNode.toFieldFeatures(): FieldFeatures =
    buildFieldFeatures(
        autofillHints = autofillHints,
        htmlAttributes = htmlInfo?.attributes?.map { it.first to it.second },
        idEntry = idEntry,
        hint = hint,
        text = text?.toString(),
    )
