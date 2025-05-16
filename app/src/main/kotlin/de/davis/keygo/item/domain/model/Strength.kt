package de.davis.keygo.item.domain.model

import androidx.annotation.IntRange

data class Strength(
    val score: Score,
    val hints: List<String> = emptyList(),
) {

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
            operator fun invoke(@IntRange(from = 1, to = 5) value: Int): Score = when (value) {
                1 -> Ridiculous
                2 -> Weak
                3 -> Moderate
                4 -> Strong
                5 -> Excellent
                else -> None
            }
        }
    }

    companion object {
        val None = Strength(Score.None)
    }
}