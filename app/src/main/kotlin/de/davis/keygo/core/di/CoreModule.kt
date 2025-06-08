package de.davis.keygo.core.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import java.security.KeyStore

@Module
@ComponentScan("de.davis.keygo.core.**")
object CoreModule {

    @Single
    internal fun provideKeyStore(): KeyStore =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
}