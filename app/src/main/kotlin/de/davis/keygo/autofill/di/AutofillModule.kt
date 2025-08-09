package de.davis.keygo.autofill.di

import de.davis.keygo.autofill.presentation.dataset.DatasetBuilderApi33Impl
import de.davis.keygo.autofill.presentation.dataset.DatasetBuilderLegacyImpl
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@ComponentScan("de.davis.keygo.autofill.**")
object AutofillModule {

    @Single
    internal fun provideDatasetBuilder() =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
            DatasetBuilderApi33Impl()
        else
            DatasetBuilderLegacyImpl()
}