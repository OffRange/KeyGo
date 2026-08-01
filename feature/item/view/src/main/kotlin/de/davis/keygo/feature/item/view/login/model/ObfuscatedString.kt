package de.davis.keygo.feature.item.view.login.model

data class ObfuscatedString(
    val raw: String,
    val formatted: String = raw,
    val visibleSuffixChars: Int = 0,
    val preservedChars: Set<Char> = emptySet(),
) {
    val hidden: String by lazy {
        val totalChars = formatted.count { it !in preservedChars }
        val reveal = if (totalChars > visibleSuffixChars) visibleSuffixChars else 0
        var charsSeen = 0
        buildString(formatted.length) {
            for (c in formatted) {
                if (c in preservedChars) append(c)
                else {
                    charsSeen++
                    append(if (totalChars - charsSeen < reveal) c else DEFAULT_OBFUSCATION_CHAR)
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_OBFUSCATION_CHAR: Char = '\u2022'
    }
}

fun String.asObfuscatedString(): ObfuscatedString = ObfuscatedString(this)
