package de.davis.keygo.rust.di

import de.davisalessandro.keygo.rust.AccountManager
import de.davisalessandro.keygo.rust.AccountManagerInterface
import de.davisalessandro.keygo.rust.RustPasskey
import de.davisalessandro.keygo.rust.RustPasskeyInterface
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@Configuration
object PasskeyModule {

    @Single
    fun providePasskeyManager(): RustPasskeyInterface = RustPasskey()

    @Single
    fun provideAccountManager(): AccountManagerInterface = AccountManager()
}