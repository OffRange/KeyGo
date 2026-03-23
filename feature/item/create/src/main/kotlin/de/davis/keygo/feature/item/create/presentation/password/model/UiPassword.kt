package de.davis.keygo.feature.item.create.presentation.password.model

data class UiPassword(val value: String) {

    val parts: List<Part> = value.splitByCharClassRegex()

    sealed interface Part {
        data class Letter(val text: String) : Part
        data class Number(val text: String) : Part
        data class Symbol(val text: String) : Part
    }

    private fun String.splitByCharClassRegex(): List<Part> {
        return PATTERN.findAll(this)
            .map {
                when {
                    it.value.all { char -> char.isLetter() } -> Part.Letter(it.value)
                    it.value.all { char -> char.isDigit() } -> Part.Number(it.value)
                    else -> Part.Symbol(it.value)
                }
            }
            .toList()
    }

    internal companion object {

        val PATTERN = Regex("""\p{L}+|\d+|[^\p{L}\d]+""")
        fun String.asUiPassword() = UiPassword(this)
    }
}