package de.davis.keygo.core.domain.crypto

import de.davis.keygo.core.domain.model.crypto.AesKey

interface EncryptionKeyProvider {

    fun provide(): AesKey
}