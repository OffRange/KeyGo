package de.davis.keygo.core.domain.model

enum class Score {
    None,
    Ridiculous,
    Weak,
    Moderate,
    Strong,
    Excellent,
    ;

    val isNone: Boolean
        get() = this == None

    companion object {
        operator fun invoke(@androidx.annotation.IntRange(from = 1, to = 5) value: Int): Score =
            when (value) {
                1 -> Ridiculous
                2 -> Weak
                3 -> Moderate
                4 -> Strong
                5 -> Excellent
                else -> None
            }
    }
}