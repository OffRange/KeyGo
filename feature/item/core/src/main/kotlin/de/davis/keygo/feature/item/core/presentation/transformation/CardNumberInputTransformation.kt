package de.davis.keygo.feature.item.core.presentation.transformation

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import de.davis.keygo.rust.card.CardFormatter
import org.koin.compose.koinInject

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

@Composable
fun rememberCardNumberInputTransformation(): CardNumberInputTransformation {
    val cardFormatter = koinInject<CardFormatter>()
    return remember(cardFormatter) { CardNumberInputTransformation(cardFormatter::digits) }
}
