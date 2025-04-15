package de.davis.keygo.core.di

import android.content.Context
import de.davis.keygo.auth.di.authModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.KoinApplication

fun KoinApplication.init(androidContext: Context) {
    androidContext(androidContext)

    // modules
    modules(coreModule, authModule)
}