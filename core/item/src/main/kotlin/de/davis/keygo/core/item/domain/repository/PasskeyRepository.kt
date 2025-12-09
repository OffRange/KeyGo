package de.davis.keygo.core.item.domain.repository

import de.davis.keygo.core.item.domain.model.Passkey

interface PasskeyRepository {

    suspend fun createPasskey(passkey: Passkey)
    suspend fun doCredentialIdsExist(credentialIds: Set<ByteArray>): Boolean
}