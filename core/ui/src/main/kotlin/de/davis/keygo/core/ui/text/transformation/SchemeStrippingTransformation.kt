package de.davis.keygo.core.ui.text.transformation

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

private val SCHEME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+\\-.]*://")

class SchemeStrippingTransformation(
    private val onSchemeChange: (String?) -> Unit,
) : InputTransformation {

    override fun TextFieldBuffer.transformInput() {
        val match = SCHEME_REGEX.find(asCharSequence()) ?: run {
            if (length == 0) onSchemeChange(null)
            return
        }

        val scheme = match.value
        onSchemeChange(scheme)
        replace(0, scheme.length, "")
        placeCursorAtEnd()
    }
}

@Composable
fun rememberSchemeStrippingTransformation(
    onSchemeChange: (String?) -> Unit,
): SchemeStrippingTransformation {
    val currentOnSchemeChange by rememberUpdatedState(onSchemeChange)
    return remember { SchemeStrippingTransformation { currentOnSchemeChange(it) } }
}
