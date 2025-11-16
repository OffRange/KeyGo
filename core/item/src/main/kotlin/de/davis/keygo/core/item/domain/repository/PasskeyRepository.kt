package de.davis.keygo.core.item.domain.repository

import de.davis.keygo.core.item.domain.model.Passkey

interface PasskeyRepository {

    fun createPasskey(passkey: Passkey)
    fun doesCredentialIdsExist(credentialIds: Set<ByteArray>): Boolean
}