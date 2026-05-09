package de.davis.keygo.core.item.domain.model

import androidx.annotation.IntRange

enum class PasswordScore {
    None,
    Ridiculous,
    Weak,
    Moderate,
    Strong,
    Excellent;

    val isNone: Boolean
        get() = this == None

    companion object {
        operator fun invoke(@IntRange(from = 1, to = 5) value: Int): PasswordScore = when (value) {
            1 -> Ridiculous
            2 -> Weak
            3 -> Moderate
            4 -> Strong
            5 -> Excellent
            else -> None
        }
    }
}