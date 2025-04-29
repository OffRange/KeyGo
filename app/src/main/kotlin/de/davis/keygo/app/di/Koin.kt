package de.davis.keygo.app.di

import android.content.Context
import de.davis.keygo.auth.di.authModule
import de.davis.keygo.core.di.coreModule
import de.davis.keygo.dashboard.di.dashboardModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.KoinApplication

fun KoinApplication.init(androidContext: Context) {
    androidContext(androidContext)

    // modules
    modules(coreModule, authModule, dashboardModule)
}