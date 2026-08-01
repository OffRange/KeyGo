package de.davis.keygo.feature.item.core.presentation.transformation

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import de.davis.keygo.rust.card.CardFormatter
import org.koin.compose.koinInject

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

/**
 * The CVV cap depends on the card network, which is derived from [numberState]'s current
 * digits, so the transformation reads that sibling field live at transform time.
 */
@Composable
fun rememberCvvInputTransformation(numberState: TextFieldState): CvvInputTransformation {
    val cardFormatter = koinInject<CardFormatter>()
    return remember(cardFormatter, numberState) {
        CvvInputTransformation { cardFormatter.cvvLen(numberState.text.toString()) }
    }
}
