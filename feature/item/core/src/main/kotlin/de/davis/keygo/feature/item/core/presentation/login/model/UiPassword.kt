package de.davis.keygo.feature.item.core.presentation.login.model

data class UiPassword(val value: String) {

    val parts: List<Part> = value.splitByCharClassRegex()

    sealed interface Part {
        val text: String

        data class Letter(override val text: String) : Part
        data class Number(override val text: String) : Part
        data class Symbol(override val text: String) : Part
    }

    private fun CharSequence.splitByCharClassRegex(): List<Part> {
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

    companion object {

        private val PATTERN = Regex("""\p{L}+|\d+|[^\p{L}\d]+""")

        fun String.asUiPassword() = UiPassword(this)
    }
}
