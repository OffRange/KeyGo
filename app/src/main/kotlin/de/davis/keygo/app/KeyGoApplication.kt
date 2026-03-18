package de.davis.keygo.app

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.annotation.KoinApplication
import org.koin.plugin.module.dsl.startKoin

@KoinApplication
class KeyGoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin<KeyGoApplication> {
            androidLogger()
            androidContext(this@KeyGoApplication)
        }
    }
}