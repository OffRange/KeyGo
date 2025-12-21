package de.davis.keygo.feature.item.create.presentation.password.model

import androidx.compose.runtime.Stable
import kotlin.experimental.and
import kotlin.experimental.or
import kotlin.experimental.xor

@JvmInline
@Stable
value class UiCharacterSet(val value: Byte) {
    companion object {
        val LOWERCASE = UiCharacterSet(0b1)
        val UPPERCASE = UiCharacterSet(0b10)
        val DIGITS = UiCharacterSet(0b100)
        val PUNCTUATIONS = UiCharacterSet(0b1000)
        val ALL = LOWERCASE or UPPERCASE or DIGITS or PUNCTUATIONS
        val NONE = UiCharacterSet(0)
    }

    fun selected(characterSet: UiCharacterSet): Boolean {
        val zero = 0.toByte()
        return when (characterSet) {
            LOWERCASE -> value and LOWERCASE.value != zero
            UPPERCASE -> value and UPPERCASE.value != zero
            DIGITS -> value and DIGITS.value != zero
            PUNCTUATIONS -> value and PUNCTUATIONS.value != zero
            else -> false
        }
    }

    fun toggle(characterSet: UiCharacterSet): UiCharacterSet {
        return when (characterSet) {
            LOWERCASE -> UiCharacterSet(value xor LOWERCASE.value)
            UPPERCASE -> UiCharacterSet(value xor UPPERCASE.value)
            DIGITS -> UiCharacterSet(value xor DIGITS.value)
            PUNCTUATIONS -> UiCharacterSet(value xor PUNCTUATIONS.value)
            else -> this
        }
    }

    infix fun or(other: UiCharacterSet): UiCharacterSet = UiCharacterSet(value or other.value)
}