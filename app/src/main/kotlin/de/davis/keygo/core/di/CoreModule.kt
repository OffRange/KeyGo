package de.davis.keygo.core.di

import de.davis.keygo.core.data.crypto.CryptographicScopeProviderImpl
import de.davis.keygo.core.data.crypto.EncryptionKeyProviderImpl
import de.davis.keygo.core.data.local.datasource.KeyGoDatabase
import de.davis.keygo.core.data.local.datasource.datastore.dataStoreModule
import de.davis.keygo.core.data.repository.MainPasswordRepositoryImpl
import de.davis.keygo.core.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.domain.crypto.EncryptionKeyProvider
import de.davis.keygo.core.domain.repository.MainPasswordRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

private val cryptoModule = module {
    singleOf(::EncryptionKeyProviderImpl) bind EncryptionKeyProvider::class
    singleOf(::CryptographicScopeProviderImpl) bind CryptographicScopeProvider::class
    singleOf(::EncryptionKeyProviderImpl) bind EncryptionKeyProvider::class
}

val coreModule = module {
    includes(dataStoreModule, KeyGoDatabase.koinModule)

    includes(cryptoModule)

    singleOf(::MainPasswordRepositoryImpl) bind MainPasswordRepository::class
}