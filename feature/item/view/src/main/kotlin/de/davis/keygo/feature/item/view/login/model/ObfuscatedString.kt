package de.davis.keygo.feature.item.view.login.model

data class ObfuscatedString(val raw: String) {
    val hidden: String
        get() = DEFAULT_OBFUSCATION_CHAR.toString().repeat(raw.length)

    companion object {
        private const val DEFAULT_OBFUSCATION_CHAR: Char = '•'
    }
}

fun String.asObfuscatedString(): ObfuscatedString = ObfuscatedString(this)
