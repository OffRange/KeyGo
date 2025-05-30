package de.davis.keygo.core.di

import de.davis.keygo.core.data.SessionImpl
import de.davis.keygo.core.data.crypto.CryptographicScopeProviderImpl
import de.davis.keygo.core.data.local.datasource.KeyGoDatabase
import de.davis.keygo.core.data.local.datasource.datastore.dataStoreModule
import de.davis.keygo.core.data.navigation.NavigatorImpl
import de.davis.keygo.core.data.repository.MainPasswordRepositoryImpl
import de.davis.keygo.core.data.repository.PasswordRepositoryImpl
import de.davis.keygo.core.data.repository.VaultItemRepositoryImpl
import de.davis.keygo.core.data.snackbar.SnackbarManagerImpl
import de.davis.keygo.core.domain.Session
import de.davis.keygo.core.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.domain.navigation.Navigator
import de.davis.keygo.core.domain.repository.MainPasswordRepository
import de.davis.keygo.core.domain.repository.PasswordRepository
import de.davis.keygo.core.domain.repository.VaultItemRepository
import de.davis.keygo.core.domain.snackbar.SnackbarManager
import de.davis.keygo.core.domain.usecase.InsertVaultItem
import de.davis.keygo.core.domain.usecase.ValidateMainPassword
import org.koin.core.module.Module
import org.koin.core.module.dsl.scopedOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.ScopeDSL
import org.koin.dsl.bind
import org.koin.dsl.module
import java.security.KeyStore

private val cryptoModule = module {
    singleOf(::MainPasswordRepositoryImpl) bind MainPasswordRepository::class

    singleOf(::PasswordRepositoryImpl) bind PasswordRepository::class
    singleOf(::VaultItemRepositoryImpl) bind VaultItemRepository::class
    singleOf(::InsertVaultItem)

    singleOf(::SnackbarManagerImpl) bind SnackbarManager::class

    singleOf(::NavigatorImpl) bind Navigator::class

    singleOf(::SessionImpl) bind Session::class
    single {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    }
    sessionScope {
        scopedOf(::ValidateMainPassword)
        scopedOf(::CryptographicScopeProviderImpl) bind CryptographicScopeProvider::class
    }
}

val coreModule = module {
    includes(dataStoreModule, KeyGoDatabase.koinModule)

    includes(cryptoModule)
}

fun Module.sessionScope(block: ScopeDSL.() -> Unit) {
    scope<SessionImpl>(block)
}