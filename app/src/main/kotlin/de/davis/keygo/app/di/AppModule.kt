package de.davis.keygo.app.di

import de.davis.keygo.autofill.di.AutofillModule
import de.davis.keygo.dashboard.di.DashboardModule
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module

@Module(
    includes = [
        AutofillModule::class,
        DashboardModule::class,
    ]
)
@Configuration
object AppModule


