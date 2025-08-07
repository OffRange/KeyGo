package de.davis.keygo.app

import android.app.Application
import de.davis.keygo.app.di.init
import org.koin.core.context.startKoin

class KeyGoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            init(this@KeyGoApplication)
        }
    }
}