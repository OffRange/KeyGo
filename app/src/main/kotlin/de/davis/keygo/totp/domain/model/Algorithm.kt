package de.davis.keygo.totp.domain.model

sealed interface Algorithm {
    data object SHA1 : Algorithm
    data object SHA256 : Algorithm
    data object SHA512 : Algorithm

    companion object {
        fun fromString(value: String): Algorithm? = when (value.uppercase()) {
            "SHA1" -> SHA1
            "SHA256" -> SHA256
            "SHA512" -> SHA512
            else -> null
        }
    }
}