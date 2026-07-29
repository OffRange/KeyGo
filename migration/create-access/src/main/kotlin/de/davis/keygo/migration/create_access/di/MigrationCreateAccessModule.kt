package de.davis.keygo.migration.create_access.di

import android.content.Context
import androidx.datastore.dataStore
import de.davis.keygo.migration.create_access.data.local.datasource.datastore.MainPasswordSerializer
import de.davis.keygo.migration.create_access.di.annotation.MainPasswordQualifier
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@Configuration
@ComponentScan("de.davis.keygo.migration.create_access")
object MigrationCreateAccessModule {

    private const val DATA_STORE_NAME = "main-password.db"

    private val Context.protoMainPasswordDataStore by dataStore(
        fileName = DATA_STORE_NAME,
        serializer = MainPasswordSerializer,
    )

    @Single
    @MainPasswordQualifier
    internal fun provideProtoMainPasswordDataStore(context: Context) =
        context.protoMainPasswordDataStore
}
