package de.davis.keygo.autofill.di

import android.content.Context
import de.davis.keygo.autofill.presentation.dataset.DatasetBuilderApi33Impl
import de.davis.keygo.autofill.presentation.dataset.DatasetBuilderLegacyImpl
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@ComponentScan("de.davis.keygo.autofill")
object AutofillModule {

    @Single
    internal fun provideDatasetBuilder() =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
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
}