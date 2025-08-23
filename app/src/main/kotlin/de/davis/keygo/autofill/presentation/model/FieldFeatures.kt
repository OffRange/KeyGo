package de.davis.keygo.autofill.presentation.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class FieldFeatures(
    val autofillHints: Set<String>,
    val htmlAttributes: Map<String, String>,
    val tokens: Set<String>,
) : Parcelable