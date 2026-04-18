package de.davis.keygo.rust.di

import de.davisalessandro.keygo.rust.AccountManager
import de.davisalessandro.keygo.rust.AccountManagerInterface
import de.davisalessandro.keygo.rust.KeyDeriver
import de.davisalessandro.keygo.rust.KeyDeriverInterface
import de.davisalessandro.keygo.rust.KeyWrapper
import de.davisalessandro.keygo.rust.KeyWrapperInterface
import de.davisalessandro.keygo.rust.RustPasskey
import de.davisalessandro.keygo.rust.RustPasskeyInterface
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@Configuration
object RustModule {

    @Single
    fun providePasskeyManager(): RustPasskeyInterface = RustPasskey()

    @Single
    fun provideAccountManager(): AccountManagerInterface = AccountManager()

    @Single
    fun provideKeyWrapper(): KeyWrapperInterface = KeyWrapper()

    @Single
    fun provideKeyDeriver(): KeyDeriverInterface = KeyDeriver()
}