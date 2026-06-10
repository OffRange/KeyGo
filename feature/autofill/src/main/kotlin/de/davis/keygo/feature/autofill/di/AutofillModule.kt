package de.davis.keygo.feature.autofill.di

import android.content.Context
import android.os.Build
import android.view.autofill.AutofillManager
import androidx.core.content.getSystemService
import de.davis.keygo.core.identity.di.CoreIdentityModule
import de.davis.keygo.core.util.di.CoreUtilModule
import de.davis.keygo.feature.autofill.presentation.dataset.DatasetBuilderApi33Impl
import de.davis.keygo.feature.autofill.presentation.dataset.DatasetBuilderLegacyImpl
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module(includes = [CoreIdentityModule::class, CoreUtilModule::class])
@Configuration
@ComponentScan("de.davis.keygo.feature.autofill")
object AutofillModule {

    @Single
    internal fun provideDatasetBuilder() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            DatasetBuilderApi33Impl()
        else
            DatasetBuilderLegacyImpl()

    @Single
    internal fun provideOkHttpClient(applicationContext: Context) = OkHttpClient.Builder()
        .cache(
            Cache(
                directory = applicationContext.cacheDir.resolve("autofill_cache"),
                maxSize = 3 * 1024 * 1024 // 3 MB
            )
        )
        .build()

    @Single
    internal fun provideAutofillManager(applicationContext: Context) =
        applicationContext.getSystemService<AutofillManager>()
}