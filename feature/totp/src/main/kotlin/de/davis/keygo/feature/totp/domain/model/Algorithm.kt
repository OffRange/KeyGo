package de.davis.keygo.feature.totp.domain.model

sealed interface Algorithm {
    val name: String

    data object SHA1 : Algorithm {
        override val name: String = "sha1"
    }

    data object SHA256 : Algorithm {
        override val name: String = "sha256"
    }

    data object SHA512 : Algorithm {
        override val name: String = "sha512"
    }

    companion object {
        fun fromString(value: String): Algorithm? = when (value.uppercase()) {
            "SHA1" -> SHA1
            "SHA256" -> SHA256
            "SHA512" -> SHA512
            else -> null
        }
    }
}