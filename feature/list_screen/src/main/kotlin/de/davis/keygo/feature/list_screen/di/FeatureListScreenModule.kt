package de.davis.keygo.feature.list_screen.di

import android.content.Context
import androidx.datastore.dataStore
import de.davis.keygo.feature.list_screen.data.local.datasource.ListSelectionSerializer
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@Configuration
@ComponentScan("de.davis.keygo.feature.list_screen")
object FeatureListScreenModule {

    private const val DATA_STORE_NAME = "list_selection.pb"

    private val Context.listSelectionDataStore by dataStore(
        fileName = DATA_STORE_NAME,
        serializer = ListSelectionSerializer,
    )

    @Single
    internal fun provideListSelectionDataStore(context: Context) =
        context.listSelectionDataStore
}
