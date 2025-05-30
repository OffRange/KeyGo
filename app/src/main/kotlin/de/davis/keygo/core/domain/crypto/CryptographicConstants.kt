package de.davis.keygo.core.domain.crypto

import javax.crypto.Cipher

object CryptographicConstants {
    const val ALGORITHM = "AES"
    const val BLOCK_MODE = "GCM"
    const val PADDING_MODE = "NoPadding"
    const val KEY_LENGTH = 256

    val DEFAULT_CIPHER: Cipher
        get() = Cipher.getInstance("$ALGORITHM/$BLOCK_MODE/$PADDING_MODE")
}