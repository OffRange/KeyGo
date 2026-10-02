package de.davis.keygo.app.di

import android.content.Context
import androidx.work.WorkManager
import de.davis.keygo.dashboard.di.DashboardModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module(
    includes = [
        DashboardModule::class,
    ]
)
@ComponentScan("de.davis.keygo.app")
@Configuration
object AppModule {

    @Single
    internal fun provideWorkManager(context: Context): WorkManager =
        WorkManager.getInstance(context)
}


