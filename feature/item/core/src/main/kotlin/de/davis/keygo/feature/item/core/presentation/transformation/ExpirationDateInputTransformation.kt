package de.davis.keygo.feature.item.core.presentation.transformation

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.runtime.Composable
import de.davis.keygo.rust.card.CardFormatter
import org.koin.compose.koinInject
import org.koin.core.annotation.Single

@Single
class ExpirationDateInputTransformation(
    private val cardFormatter: CardFormatter
) : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val original = asCharSequence().toString()
        val formatted = cardFormatter.formatExpirationAfterEdit(originalText.toString(), original)
        if (original != formatted) {
            replace(0, length, formatted)
            placeCursorAtEnd()
        }
    }
}

@Composable
fun rememberExpirationDateInputTransformation(): ExpirationDateInputTransformation {
    return koinInject<ExpirationDateInputTransformation>()
}
