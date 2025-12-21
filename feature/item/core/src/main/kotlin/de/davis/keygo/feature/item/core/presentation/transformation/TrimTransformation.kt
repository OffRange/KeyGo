package de.davis.keygo.feature.item.core.presentation.transformation

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.placeCursorAtEnd

object TrimTransformation : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val original = asCharSequence().toString()
        val trimmed = original.trimStart()
        if (original != trimmed) {
            replace(0, length, trimmed)
            placeCursorAtEnd()
        }
    }
}