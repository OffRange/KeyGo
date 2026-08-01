package de.davis.keygo.core.item.domain.repository

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.Passkey
import de.davis.keygo.core.item.domain.model.PasskeyMetadata

interface PasskeyRepository {

    suspend fun createPasskey(passkey: Passkey)
    suspend fun doCredentialIdsExist(credentialIds: Set<ByteArray>): Boolean

    suspend fun getPasskeysForRP(rpId: String): List<PasskeyMetadata>

    suspend fun getPasskey(credentialId: ByteArray): Passkey?

    suspend fun getPasskeysByLogin(loginId: ItemId): List<Passkey>
}