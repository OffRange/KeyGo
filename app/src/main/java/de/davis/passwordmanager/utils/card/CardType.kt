package de.davis.passwordmanager.utils.card

enum class CardType(
    val prefixes: List<String>,
    val lengthRange: IntRange,
    val formatter: Formatter
) {
    Visa(listOf("4"), 16..16, Formatter.FourDigitChunkFormatter),
    MasterCard((1..5).map { "5$it" }, 16..16, Formatter.FourDigitChunkFormatter),
    AmericanExpress(listOf("34", "37"), 15..15, Formatter.FourSixRemainderChunkFormatter),
    Discover(listOf("6011", "65"), 16..16, Formatter.FourDigitChunkFormatter),
    JCB(listOf("35"), 16..19, Formatter.FourDigitChunkFormatter),
    DinnersClub(listOf("36", "38", "39"), 14..14, Formatter.FourSixRemainderChunkFormatter),

    Unknown(emptyList(), 14..19, Formatter.FourDigitChunkFormatter);

    companion object {
        fun getTypeByNumber(cardNumber: String) = entries.firstOrNull {
            it.prefixes.any { prefix -> cardNumber.startsWith(prefix) }
        } ?: Unknown
    }
}