package de.davis.keygo.rust

import de.davisalessandro.keygo.rust.CardFormatterInterface

class FakeCardFormatter : CardFormatterInterface {

    var digitsResult: (String) -> String = { input -> input.filter(Char::isDigit) }
    var formatResult: (String) -> String = { it.chunked(4).joinToString(" ") }
    var spaceIndicesResult: (String) -> List<Int> = { emptyList() }
    var luhnResult: Boolean = true

    override fun digits(input: String): String = digitsResult(input)

    override fun formatNumber(input: String): String = formatResult(input)

    override fun spaceIndices(input: String): List<Int> = spaceIndicesResult(input)

    override fun isLuhnValid(input: String): Boolean = luhnResult
}
