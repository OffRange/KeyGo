package de.davis.keygo.feature.backup.domain.repository

import de.davis.keygo.core.security.domain.crypto.model.CryptographicData

interface BackupArkKeyStore {
    suspend fun save(data: CryptographicData)
    suspend fun load(): CryptographicData?
    suspend fun clear()
}
