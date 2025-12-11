package de.davis.keygo.core.identity.biometric.data.repository

import androidx.datastore.core.DataStore
import de.davis.keygo.auth.data.local.model.ProtoBiometricKeyData
import de.davis.keygo.core.di.annotation.BiometricQualifier
import de.davis.keygo.core.identity.biometric.data.mapper.toDomain
import de.davis.keygo.core.identity.biometric.data.mapper.toProto
import de.davis.keygo.core.identity.common.data.repository.DefaultWrappedKeyRepository
import de.davis.keygo.core.identity.common.domain.model.BiometricWrappedKeyData
import de.davis.keygo.core.identity.common.domain.repository.WrappedKeyRepository
import org.koin.core.annotation.Single

@Suppress("FunctionName")
@Single(binds = [WrappedKeyRepository::class])
@BiometricQualifier
@Deprecated("Migrate to :core:security")
fun BiometricWrappedKeyRepositoryImpl(@BiometricQualifier dataStore: DataStore<ProtoBiometricKeyData>) =
    DefaultWrappedKeyRepository(
        dataStore = dataStore,
        toDomain = ProtoBiometricKeyData::toDomain,
        toProto = BiometricWrappedKeyData::toProto,
        defaultInstance = ProtoBiometricKeyData::getDefaultInstance
    )