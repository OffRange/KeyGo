package de.davis.keygo.auth.data.repository

import androidx.datastore.core.DataStore
import de.davis.keygo.auth.data.local.model.ProtoPasswordKeyData
import de.davis.keygo.auth.data.mapper.toDomain
import de.davis.keygo.auth.data.mapper.toProto
import de.davis.keygo.auth.domain.model.PasswordWrappedKeyData
import de.davis.keygo.core.di.annotation.PasswordQualifier
import de.davis.keygo.core.identity.common.data.repository.DefaultWrappedKeyRepository
import de.davis.keygo.core.identity.common.domain.repository.WrappedKeyRepository
import org.koin.core.annotation.Single

@Suppress("FunctionName")
@Single(binds = [WrappedKeyRepository::class])
@PasswordQualifier
fun PasswordWrappedKeyRepositoryImpl(@PasswordQualifier dataStore: DataStore<ProtoPasswordKeyData>) =
    DefaultWrappedKeyRepository(
        dataStore = dataStore,
        toDomain = ProtoPasswordKeyData::toDomain,
        toProto = PasswordWrappedKeyData::toProto,
        defaultInstance = ProtoPasswordKeyData::getDefaultInstance
    )