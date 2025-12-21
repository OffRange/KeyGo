package de.davis.keygo.app.di

import android.content.Context
import de.davis.keygo.auth.di.AuthModule
import de.davis.keygo.autofill.di.AutofillModule
import de.davis.keygo.core.di.CoreModule
import de.davis.keygo.core.item.data.local.datasource.ItemDatabase
import de.davis.keygo.core.item.di.CoreItemModule
import de.davis.keygo.dashboard.di.DashboardModule
import de.davis.keygo.feature.credentials.di.FeatureCredentialsModule
import de.davis.keygo.feature.item.core.di.FeatureItemCoreModule
import de.davis.keygo.feature.item.create.di.FeatureItemCreateModule
import de.davis.keygo.feature.list_screen.di.FeatureListScreenModule
import de.davis.keygo.feature.totp.di.FeatureTotpModule
import de.davis.keygo.item.di.ItemModule
import de.davis.keygo.migration.create_access.di.MigrationCreateAccessModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.KoinApplication
import org.koin.ksp.generated.module

fun KoinApplication.init(androidContext: Context) {
    androidContext(androidContext)

    // modules
    modules(
        ItemDatabase.koinModule,
        CoreModule.module,
        CoreItemModule.module,
        FeatureListScreenModule.module,
        FeatureCredentialsModule.module,
        FeatureItemCoreModule.module,
        FeatureItemCreateModule.module,
        FeatureTotpModule.module,
        AuthModule.module,
        DashboardModule.module,
        ItemModule.module,
        AutofillModule.module,

        MigrationCreateAccessModule.module
    )
}