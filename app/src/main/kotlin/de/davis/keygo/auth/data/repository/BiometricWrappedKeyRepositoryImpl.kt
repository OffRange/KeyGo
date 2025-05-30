package de.davis.keygo.auth.data.repository

import androidx.datastore.core.DataStore
import com.google.protobuf.kotlin.toByteString
import de.davis.keygo.auth.data.local.model.ProtoBiometricKeyData
import de.davis.keygo.auth.data.local.model.copy
import de.davis.keygo.auth.data.mapper.toDomain
import de.davis.keygo.auth.domain.model.BiometricWrappedKeyData
import de.davis.keygo.auth.domain.repository.BiometricWrappedKeyRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

class BiometricWrappedKeyRepositoryImpl(
    private val dataStore: DataStore<ProtoBiometricKeyData>
) : BiometricWrappedKeyRepository {

    override suspend fun getBiometricWrappedKeyData(): BiometricWrappedKeyData? =
        dataStore.data.map(ProtoBiometricKeyData::toDomain).firstOrNull()

    override suspend fun setBiometricWrappedKeyData(wrappedKey: ByteArray?, iv: ByteArray?) {
        dataStore.updateData {
            it.copy {
                wrappedKey?.let { newKey -> key = newKey.toByteString() }
                iv?.let { newIv -> keyIV = newIv.toByteString() }
            }
        }
    }
}