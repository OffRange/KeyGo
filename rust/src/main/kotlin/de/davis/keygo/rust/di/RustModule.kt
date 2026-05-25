package de.davis.keygo.rust.di

import de.davisalessandro.keygo.rust.AccountManager
import de.davisalessandro.keygo.rust.AccountManagerInterface
import de.davisalessandro.keygo.rust.CardFormatter
import de.davisalessandro.keygo.rust.CardFormatterInterface
import de.davisalessandro.keygo.rust.ItemManager
import de.davisalessandro.keygo.rust.ItemManagerInterface
import de.davisalessandro.keygo.rust.KeyDeriver
import de.davisalessandro.keygo.rust.KeyDeriverInterface
import de.davisalessandro.keygo.rust.KeyWrapper
import de.davisalessandro.keygo.rust.KeyWrapperInterface
import de.davisalessandro.keygo.rust.RustPasskey
import de.davisalessandro.keygo.rust.RustPasskeyInterface
import de.davisalessandro.keygo.rust.TotpService
import de.davisalessandro.keygo.rust.TotpServiceInterface
import de.davisalessandro.keygo.rust.VaultManager
import de.davisalessandro.keygo.rust.VaultManagerInterface
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@Configuration
object RustModule {

    @Single
    internal fun providePasskeyManager(): RustPasskeyInterface = RustPasskey()

    @Single
    internal fun provideAccountManager(): AccountManagerInterface = AccountManager()

    @Single
    internal fun provideKeyWrapper(): KeyWrapperInterface = KeyWrapper()

    @Single
    internal fun provideKeyDeriver(): KeyDeriverInterface = KeyDeriver()

    @Single
    internal fun provideItemManager(): ItemManagerInterface = ItemManager()

    @Single
    internal fun provideVaultManager(): VaultManagerInterface = VaultManager()

    @Single
    internal fun provideTotpService(): TotpServiceInterface = TotpService()

    @Single
    internal fun provideCardFormatter(): CardFormatterInterface = CardFormatter()
}