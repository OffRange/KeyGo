package de.davis.keygo.feature.backup

import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.feature.backup.domain.repository.BackupArkKeyStore

class FakeBackupArkKeyStore(
    private var stored: CryptographicData? = null,
) : BackupArkKeyStore {

    var clearCount = 0
        private set

    override suspend fun save(data: CryptographicData) {
        stored = data
    }

    override suspend fun load(): CryptographicData? = stored

    override suspend fun clear() {
        clearCount++
        stored = null
    }
}
