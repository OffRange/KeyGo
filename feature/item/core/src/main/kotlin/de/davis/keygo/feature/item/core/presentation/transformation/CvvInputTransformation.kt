package de.davis.keygo.feature.item.core.presentation.transformation

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.placeCursorAtEnd

class CvvInputTransformation(private val maxLength: () -> Int) : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val original = asCharSequence().toString()
        // Never shorten below what was already there; only refuse to grow past the network max.
        val cap = maxOf(maxLength(), originalText.length)
        val sanitized = original.filter(Char::isDigit).take(cap)
        if (original != sanitized) {
            replace(0, length, sanitized)
            placeCursorAtEnd()
        }
    }
}
