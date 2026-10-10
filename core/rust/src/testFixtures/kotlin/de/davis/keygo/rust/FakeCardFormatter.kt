package de.davis.keygo.rust

import de.davisalessandro.keygo.rust.CardFormatterInterface

class FakeCardFormatter : CardFormatterInterface {

    var digitsResult: (String) -> String = { input -> input.filter(Char::isDigit) }
    var formatResult: (String) -> String = { it.chunked(4).joinToString(" ") }
    var spaceIndicesResult: (String) -> List<Int> = { emptyList() }
    var validResult: (String) -> Boolean = { true }
    var cvvLenResult: (String) -> Int = { 3 }
    var formatExpirationAfterEditResult: (String, String) -> String = { _, proposed -> proposed }

    override fun digits(input: String): String = digitsResult(input)

    override fun formatNumber(input: String): String = formatResult(input)

    override fun spaceIndices(input: String): List<Int> = spaceIndicesResult(input)

    override fun isValid(input: String): Boolean = validResult(input)

    override fun cvvLen(input: String): Int = cvvLenResult(input)

    override fun formatExpirationAfterEdit(previous: String, proposed: String): String =
        formatExpirationAfterEditResult(previous, proposed)
}
