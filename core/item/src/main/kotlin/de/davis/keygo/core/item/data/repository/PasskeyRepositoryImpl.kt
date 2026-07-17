package de.davis.keygo.core.item.data.repository

import de.davis.keygo.core.item.data.local.dao.PasskeyDao
import de.davis.keygo.core.item.data.local.entity.credential.PasskeyEntity
import de.davis.keygo.core.item.data.local.pojo.PasskeyMetadataPojo
import de.davis.keygo.core.item.data.mapper.toData
import de.davis.keygo.core.item.data.mapper.toDomain
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.Passkey
import de.davis.keygo.core.item.domain.model.PasskeyMetadata
import de.davis.keygo.core.item.domain.repository.PasskeyRepository
import org.koin.core.annotation.Single

@Single
internal class PasskeyRepositoryImpl(
    private val passkeyDao: PasskeyDao
) : PasskeyRepository {

    override suspend fun createPasskey(passkey: Passkey) =
        passkeyDao.insertPasskey(passkey.toData())

    override suspend fun doCredentialIdsExist(credentialIds: Set<ByteArray>): Boolean =
        passkeyDao.doesCredentialIdsExist(credentialIds)

    override suspend fun getPasskeysForRP(rpId: String): List<PasskeyMetadata> =
        passkeyDao.getPasskeysForRP(rpId)
            .map(PasskeyMetadataPojo::toDomain)

    override suspend fun getPasskey(credentialId: ByteArray): Passkey? =
        passkeyDao.getPasskey(credentialId)?.toDomain()

    override suspend fun getPasskeysByLogin(loginId: ItemId): List<Passkey> =
        passkeyDao.getPasskeysForLogin(loginId).map(PasskeyEntity::toDomain)
}