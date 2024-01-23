package de.davis.passwordmanager.utils.card

sealed class Formatter {

    data object FourDigitChunkFormatter : Formatter() {

        override fun runFormat(cardNumber: String): String = cardNumber.chunked(4).joinToString(" ")
    }


    data object FourSixRemainderChunkFormatter : Formatter() {

        override fun runFormat(cardNumber: String): String = when {
            cardNumber.length <= 4 -> cardNumber
            cardNumber.length <= 10 -> cardNumber.take(4) + " " + cardNumber.substring(4)
            else -> cardNumber.substring(0, 4) + " " + cardNumber.substring(4, 10) +
                    " " + cardNumber.substring(10)
        }
    }

    protected abstract fun runFormat(cardNumber: String): String

    fun format(cardNumber: String): String {
        return runFormat(cardNumber.replace(" ", ""))
    }
}