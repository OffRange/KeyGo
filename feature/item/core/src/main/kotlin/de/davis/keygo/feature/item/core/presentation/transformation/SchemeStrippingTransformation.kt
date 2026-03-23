package de.davis.keygo.feature.item.core.presentation.transformation

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

private val SCHEME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+\\-.]*://")

class SchemeStrippingTransformation : InputTransformation {

    private val _detectedScheme = mutableStateOf<String?>(null)

    val detectedScheme: State<String?> get() = _detectedScheme

    override fun TextFieldBuffer.transformInput() {
        val text = asCharSequence().toString()
        val match = SCHEME_REGEX.find(text) ?: run {
            if (text.isEmpty()) _detectedScheme.value = null
            return
        }

        val scheme = match.value
        _detectedScheme.value = scheme
        replace(0, scheme.length, "")
        placeCursorAtEnd()
    }
}

@Composable
fun rememberSchemeStrippingTransformation(): SchemeStrippingTransformation {
    return remember { SchemeStrippingTransformation() }
}
