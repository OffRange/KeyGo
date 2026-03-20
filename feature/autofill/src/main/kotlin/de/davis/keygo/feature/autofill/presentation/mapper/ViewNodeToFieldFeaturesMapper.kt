package de.davis.keygo.feature.autofill.presentation.mapper

import android.app.assist.AssistStructure
import de.davis.keygo.feature.autofill.presentation.model.FieldFeatures

internal fun AssistStructure.ViewNode.toFieldFeatures(): FieldFeatures {
    val autofillHints = autofillHints
        ?.filterNot { it.isNullOrBlank() }
        ?.toSet()
        ?: emptySet()

    val htmlAttributes = htmlInfo
        ?.attributes
        ?.filterNot { it.first.isNullOrBlank() || it.second.isNullOrBlank() }
        ?.associate { it.first to it.second }
        ?: emptyMap()

    val tokens = listOfNotNull(
        idEntry?.ifBlank { null },
        hint?.ifBlank { null },
        text?.ifBlank { null },
    ).map(CharSequence::toString)
        .map(String::lowercase).toSet()


    return FieldFeatures(
        autofillHints = autofillHints,
        htmlAttributes = htmlAttributes,
        tokens = tokens,
    )
}