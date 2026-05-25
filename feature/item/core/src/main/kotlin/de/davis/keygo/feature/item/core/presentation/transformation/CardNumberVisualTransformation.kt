package de.davis.keygo.feature.item.core.presentation.transformation

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.insert
import androidx.compose.runtime.Composable
import de.davis.keygo.rust.card.CardFormatter
import org.koin.compose.koinInject
import org.koin.core.annotation.Single

@Single
class CardNumberVisualTransformation(
    private val cardFormatter: CardFormatter
) : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        val digits = asCharSequence().toString()
        if (digits.isEmpty()) return

        // Each insert shifts later offsets right by one, so add the count of
        // spaces already inserted to keep the offsets aligned with the buffer.
        cardFormatter.spaceIndices(digits).forEachIndexed { inserted, index ->
            val at = index + inserted
            insert(at, " ")
        }
    }
}

@Composable
fun rememberCardFormatter(): CardNumberVisualTransformation {
    return koinInject<CardNumberVisualTransformation>()
}
