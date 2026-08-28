package de.davis.keygo.feature.item.core.presentation.login

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import de.davis.keygo.feature.item.core.presentation.login.model.UiPassword

@Composable
fun UiPassword.colored(
    numberColor: Color = MaterialTheme.colorScheme.primary,
    symbolColor: Color = MaterialTheme.colorScheme.tertiary,
): AnnotatedString = remember(this, numberColor, symbolColor) {
    buildAnnotatedString {
        parts.forEach {
            when (it) {
                is UiPassword.Part.Letter -> append(it.text)
                is UiPassword.Part.Number -> withStyle(SpanStyle(color = numberColor)) {
                    append(it.text)
                }

                is UiPassword.Part.Symbol -> withStyle(SpanStyle(color = symbolColor)) {
                    append(it.text)
                }
            }
        }
    }
}
