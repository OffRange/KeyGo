package de.davis.keygo.viewing.presentation.model

data class ObfuscatedString(val raw: String) {
    val hidden: String
        get() = DEFAULT_OBFUSCATION_CHAR.toString().repeat(raw.length)

    companion object {
        private const val DEFAULT_OBFUSCATION_CHAR: Char = '\u2022'
    }
}

fun String.asObfuscatedString(): ObfuscatedString = ObfuscatedString(this)