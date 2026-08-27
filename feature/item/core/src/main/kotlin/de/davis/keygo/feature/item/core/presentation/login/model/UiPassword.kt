package de.davis.keygo.feature.item.core.presentation.login.model

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

data class UiPassword(val value: String) {

    val parts: List<Part> = value.splitByCharClassRegex()

    sealed interface Part {
        val text: String

        data class Letter(override val text: String) : Part
        data class Number(override val text: String) : Part
        data class Symbol(override val text: String) : Part
    }

    private fun CharSequence.splitByCharClassRegex(): List<Part> {
        return PATTERN.findAll(this)
            .map {
                when {
                    it.value.all { char -> char.isLetter() } -> Part.Letter(it.value)
                    it.value.all { char -> char.isDigit() } -> Part.Number(it.value)
                    else -> Part.Symbol(it.value)
                }
            }
            .toList()
    }

    companion object {

        val PATTERN = Regex("""\p{L}+|\d+|[^\p{L}\d]+""")
        fun String.asUiPassword() = UiPassword(this)
    }
}

@Composable
fun UiPassword.colored(
    numberColor: Color = MaterialTheme.colorScheme.primary,
    symbolColor: Color = MaterialTheme.colorScheme.tertiary
) = remember(this, numberColor, symbolColor) {
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
