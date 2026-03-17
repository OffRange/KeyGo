package de.davis.keygo.core.identity.data

import androidx.datastore.core.DataStore
import de.davis.keygo.core.identity.data.local.model.ProtoBiometricKeyData
import de.davis.keygo.core.identity.data.local.model.ProtoPasswordKeyData
import de.davis.keygo.core.identity.data.mapper.toDomain
import de.davis.keygo.core.identity.data.mapper.toProto
import de.davis.keygo.core.identity.di.annotation.BiometricQualifier
import de.davis.keygo.core.identity.di.annotation.PasswordQualifier
import de.davis.keygo.core.identity.domain.model.BiometricWrappedKeyData
import de.davis.keygo.core.identity.domain.model.PasswordWrappedKeyData
import de.davis.keygo.core.identity.domain.repository.WrappedKeyRepository
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.asResult
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.annotation.Single

@Single
internal class WrappedKeyRepositoryImpl(
    @param:BiometricQualifier
    private val biometricDataStore: DataStore<ProtoBiometricKeyData>,
    @param:PasswordQualifier
    private val passwordDataStore: DataStore<ProtoPasswordKeyData>,
) : WrappedKeyRepository {

    override suspend fun getBiometricWrappedKey(): Result<BiometricWrappedKeyData, Unit> =
        biometricDataStore.data.firstOrNull()
            ?.toDomain()
            ?.takeIf(BiometricWrappedKeyData::isValid)
            .asResult(Unit)

    override suspend fun getPasswordWrappedKey(): Result<PasswordWrappedKeyData, Unit> =
        passwordDataStore.data.firstOrNull()
            ?.toDomain()
            ?.takeIf(PasswordWrappedKeyData::isValid)
            .asResult(Unit)

    override suspend fun setBiometricWrappedKey(data: BiometricWrappedKeyData) {
        biometricDataStore.updateData { data.toProto() }
    }

    override suspend fun setPasswordWrappedKey(data: PasswordWrappedKeyData) {
        passwordDataStore.updateData { data.toProto() }
    }
}