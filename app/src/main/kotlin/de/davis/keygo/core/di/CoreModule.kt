package de.davis.keygo.core.di

import de.davis.keygo.core.data.local.datasource.datastore.dataStoreModule
import de.davis.keygo.core.data.repository.MainPasswordRepositoryImpl
import de.davis.keygo.core.domain.repository.MainPasswordRepository
import de.davis.keygo.core.domain.usecase.ValidateMainPassword
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.ksp.generated.module
import java.security.KeyStore

@Deprecated("")
private val cryptoModule = module {

    // TODO migrate
    singleOf(::MainPasswordRepositoryImpl) bind MainPasswordRepository::class
    singleOf(::ValidateMainPassword)
}

val coreModule = module {
    includes(dataStoreModule)

    includes(cryptoModule)

    includes(CoreModule.module)
}


@Module
@ComponentScan("de.davis.keygo.core.**")
object CoreModule {

    @Single
    fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
}