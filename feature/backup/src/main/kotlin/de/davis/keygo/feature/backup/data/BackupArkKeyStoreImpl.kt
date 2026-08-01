package de.davis.keygo.feature.backup.data

import androidx.datastore.core.DataStore
import com.google.protobuf.kotlin.toByteString
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.feature.backup.data.local.model.ProtoBackupArkData
import de.davis.keygo.feature.backup.data.local.model.protoBackupArkData
import de.davis.keygo.feature.backup.di.annotation.BackupArkQualifier
import de.davis.keygo.feature.backup.domain.repository.BackupArkKeyStore
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single

@Single
internal class BackupArkKeyStoreImpl(
    @param:BackupArkQualifier
    private val dataStore: DataStore<ProtoBackupArkData>,
) : BackupArkKeyStore {

    override suspend fun save(data: CryptographicData) {
        dataStore.updateData {
            protoBackupArkData {
                ct = data.data.toByteString()
                iv = data.iv.toByteString()
            }
        }
    }

    override suspend fun load(): CryptographicData? {
        val proto = dataStore.data.first()
        if (proto.ct.isEmpty || proto.iv.isEmpty) return null
        return CryptographicData(proto.ct.toByteArray(), proto.iv.toByteArray())
    }

    override suspend fun clear() {
        dataStore.updateData { it.toBuilder().clearCt().clearIv().build() }
    }
}
