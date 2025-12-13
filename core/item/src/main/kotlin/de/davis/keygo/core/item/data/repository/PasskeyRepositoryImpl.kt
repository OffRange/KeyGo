package de.davis.keygo.core.item.data.repository

import de.davis.keygo.core.item.data.local.dao.PasskeyDao
import de.davis.keygo.core.item.data.maper.toData
import de.davis.keygo.core.item.domain.model.Passkey
import de.davis.keygo.core.item.domain.repository.PasskeyRepository
import org.koin.core.annotation.Single

@Single
internal class PasskeyRepositoryImpl(
    private val passkeyDao: PasskeyDao
) : PasskeyRepository {

    override suspend fun createPasskey(passkey: Passkey) =
        passkeyDao.insertPasskey(passkey.toData())

    override suspend fun doCredentialIdsExist(credentialIds: Set<ByteArray>): Boolean =
        passkeyDao.doesCredentialIdsExist(credentialIds.map { it.toHexString() }.toSet())
}