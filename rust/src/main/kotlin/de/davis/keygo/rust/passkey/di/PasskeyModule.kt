package de.davis.keygo.rust.passkey.di

import de.davisalessandro.keygo.rust.RustPasskey
import de.davisalessandro.keygo.rust.RustPasskeyInterface
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@Configuration
@ComponentScan("de.davis.keygo.rust.passkey")
object PasskeyModule {

    @Single(binds = [RustPasskeyInterface::class])
    fun providePasskeyManager(): RustPasskeyInterface = RustPasskey()
}