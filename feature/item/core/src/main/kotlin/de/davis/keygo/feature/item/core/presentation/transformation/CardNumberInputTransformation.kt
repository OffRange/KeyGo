package de.davis.keygo.feature.item.core.presentation.transformation

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.placeCursorAtEnd

class CardNumberInputTransformation(
    private val sanitize: (String) -> String,
) : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val original = asCharSequence().toString()
        val sanitized = sanitize(original)
        if (original != sanitized) {
            replace(0, length, sanitized)
            placeCursorAtEnd()
        }
    }
}
