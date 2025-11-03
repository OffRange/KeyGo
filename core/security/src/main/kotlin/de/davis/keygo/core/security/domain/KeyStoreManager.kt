package de.davis.keygo.core.security.domain

import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyInfo
import javax.crypto.Cipher

interface KeyStoreManager {

    fun getOrCreateCipherFor(keyInfo: KeyInfo, cryptographicMode: CryptographicMode): Cipher
}