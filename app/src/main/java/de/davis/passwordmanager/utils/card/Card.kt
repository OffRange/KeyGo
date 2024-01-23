package de.davis.passwordmanager.utils.card

import de.davis.passwordmanager.utils.card.algorithm.LuhnAlgorithm

class Card(val rawNumber: String, val type: CardType) {
    val cardNumber: String = type.formatter.format(rawNumber)

    fun mask() = type.formatter.format(rawNumber.replace("\\d(?=\\d{4})".toRegex(), "•"))

    fun isValidLuhnNumber(): Boolean = LuhnAlgorithm.isValid(rawNumber)

    fun isValidLength(): Boolean = type.lengthRange.contains(rawNumber.length)
}