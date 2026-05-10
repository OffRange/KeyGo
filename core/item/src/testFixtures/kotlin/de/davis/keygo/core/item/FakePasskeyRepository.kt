package de.davis.keygo.core.item

import de.davis.keygo.core.item.domain.model.Passkey
import de.davis.keygo.core.item.domain.model.PasskeyMetadata
import de.davis.keygo.core.item.domain.repository.PasskeyRepository

class FakePasskeyRepository : PasskeyRepository {

    private val store = mutableListOf<Passkey>()

    fun seed(vararg passkeys: Passkey) {
        store += passkeys
    }

    override suspend fun createPasskey(passkey: Passkey) {
        store += passkey
    }

    override suspend fun doCredentialIdsExist(credentialIds: Set<ByteArray>): Boolean =
        store.any { p -> credentialIds.any { it.contentEquals(p.credentialId) } }

    override suspend fun getPasskeysForRP(rpId: String): List<PasskeyMetadata> = emptyList()

    override suspend fun getPasskey(credentialId: ByteArray): Passkey? =
        store.firstOrNull { it.credentialId.contentEquals(credentialId) }
}
