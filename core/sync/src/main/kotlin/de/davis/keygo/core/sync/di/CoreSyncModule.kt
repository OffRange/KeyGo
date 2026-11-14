package de.davis.keygo.core.sync.di

import de.davis.keygo.core.sync.data.OkHttpTransport
import de.davisalessandro.keygo.rust.HttpTransport
import okhttp3.OkHttpClient
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@Configuration
@ComponentScan("de.davis.keygo.core.sync")
object CoreSyncModule {

    @Single
    internal fun provideHttpTransport(): HttpTransport = OkHttpTransport(OkHttpClient())
}
