package de.davis.keygo

import android.app.Application
import de.davis.keygo.core.di.coreModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.context.startKoin

class KeyGoApplication : Application() {

    @OptIn(KoinInternalApi::class)
    override fun onCreate() {
        super.onCreate()

        // Initialize Koin
        startKoin {
            androidContext(this@KeyGoApplication)

            // modules
            modules(coreModule)
        }
    }
}