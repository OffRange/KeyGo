package de.davis.keygo.app.di

import android.content.Context
import de.davis.keygo.auth.di.AuthModule
import de.davis.keygo.core.data.local.datasource.KeyGoDatabase
import de.davis.keygo.core.di.CoreModule
import de.davis.keygo.dashboard.di.DashboardModule
import de.davis.keygo.item.di.ItemModule
import de.davis.keygo.migration.create_access.di.MigrationCreateAccessModule
import de.davis.keygo.totp.di.TotpModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.KoinApplication
import org.koin.ksp.generated.module

fun KoinApplication.init(androidContext: Context) {
    androidContext(androidContext)

    // modules
    modules(
        KeyGoDatabase.koinModule,
        CoreModule.module,
        AuthModule.module,
        DashboardModule.module,
        ItemModule.module,
        TotpModule.module,

        MigrationCreateAccessModule.module
    )
}