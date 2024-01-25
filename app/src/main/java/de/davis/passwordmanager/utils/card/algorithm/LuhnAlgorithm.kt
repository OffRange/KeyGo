package de.davis.passwordmanager.utils.card.algorithm

object LuhnAlgorithm {

    fun isValid(cardNumber: String): Boolean {
        val sanitizedNumber = cardNumber.filter { it.isDigit() }
        return sanitizedNumber.reversed().mapIndexed { index, c ->
            val digit = c.digitToInt()
            if (index % 2 == 1) (digit * 2).let { if (it > 9) it - 9 else it } else digit
        }.sum() % 10 == 0
    }
}