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

    override fun createPasskey(passkey: Passkey) = passkeyDao.insertPasskey(passkey.toData())

    override fun doesCredentialIdExist(credentialId: ByteArray): Boolean =
        passkeyDao.doesCredentialIdExist(credentialId)
}