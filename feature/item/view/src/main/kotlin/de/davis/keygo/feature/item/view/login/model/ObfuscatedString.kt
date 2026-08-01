package de.davis.keygo.feature.item.view.login.model

data class ObfuscatedString(
    val raw: String,
    val formatted: String = raw,
    val visibleSuffixDigits: Int = 0,
) {
    val hidden: String by lazy {
        val totalDigits = formatted.count(Char::isDigit)
        val reveal = if (totalDigits > visibleSuffixDigits) visibleSuffixDigits else 0
        var digitsSeen = 0
        buildString(formatted.length) {
            for (c in formatted) {
                if (c.isDigit()) {
                    digitsSeen++
                    append(if (totalDigits - digitsSeen < reveal) c else DEFAULT_OBFUSCATION_CHAR)
                } else append(c)
            }
        }
    }

    companion object {
        private const val DEFAULT_OBFUSCATION_CHAR: Char = '\u2022'
    }
}

fun String.asObfuscatedString(): ObfuscatedString = ObfuscatedString(this)
