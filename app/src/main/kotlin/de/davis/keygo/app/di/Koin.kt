package de.davis.keygo.app.di

import android.content.Context
import de.davis.keygo.auth.di.AuthModule
import de.davis.keygo.core.di.coreModule
import de.davis.keygo.dashboard.di.DashboardModule
import de.davis.keygo.item.di.ItemModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.KoinApplication
import org.koin.ksp.generated.module

fun KoinApplication.init(androidContext: Context) {
    androidContext(androidContext)

    // modules
    modules(coreModule, AuthModule.module, DashboardModule.module, ItemModule.module)
}