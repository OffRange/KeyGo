package de.davis.keygo.feature.account.presentation.setup.transformation

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.then
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextRange
import de.davis.keygo.rust.username.UsernameValidator
import org.koin.compose.koinInject

internal class UsernameInputTransformation(
    private val sanitize: (String) -> String,
) : InputTransformation {

    override fun TextFieldBuffer.transformInput() {
        val text = asCharSequence().toString()
        val sanitized = sanitize(text)
        if (sanitized == text) return

        // Keep the cursor behind what was typed rather than jumping to the end.
        val cursor = sanitize(text.substring(0, selection.end)).length
            .coerceAtMost(sanitized.length)
        replace(0, length, sanitized)
        selection = TextRange(cursor)
    }
}

@Composable
internal fun rememberUsernameInputTransformation(): InputTransformation {
    val validator = koinInject<UsernameValidator>()
    return remember(validator) {
        UsernameInputTransformation(validator::sanitize)
            .then(InputTransformation.maxLength(validator.maxLen().toInt()))
    }
}
