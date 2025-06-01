package de.davis.keygo.auth.data.repository

import androidx.datastore.core.DataStore
import de.davis.keygo.auth.data.local.model.ProtoBiometricKeyData
import de.davis.keygo.auth.data.mapper.toDomain
import de.davis.keygo.auth.data.mapper.toProto
import de.davis.keygo.auth.di.annotation.BiometricQualifier
import de.davis.keygo.auth.domain.model.BiometricWrappedKeyData
import de.davis.keygo.auth.domain.repository.WrappedKeyRepository
import org.koin.core.annotation.Single

typealias BiometricWrappedKeyRepository = WrappedKeyRepository<BiometricWrappedKeyData>

@Suppress("FunctionName")
@Single(binds = [WrappedKeyRepository::class])
@BiometricQualifier
fun BiometricWrappedKeyRepositoryImpl(@BiometricQualifier dataStore: DataStore<ProtoBiometricKeyData>) =
    DefaultWrappedKeyRepository(
        dataStore = dataStore,
        toDomain = ProtoBiometricKeyData::toDomain,
        toProto = BiometricWrappedKeyData::toProto,
        defaultInstance = ProtoBiometricKeyData::getDefaultInstance
    )