package de.davis.keygo.legacy_migration.di

import android.content.Context
import androidx.datastore.dataStore
import de.davis.keygo.legacy_migration.data.local.datasource.datastore.MainPasswordSerializer
import de.davis.keygo.legacy_migration.di.annotation.MainPasswordQualifier
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * The file name is the on-disk identity of a shipped v1 install's main password record. It must
 * never change, whatever the module or package around it is called.
 */
internal const val MAIN_PASSWORD_DATA_STORE_NAME = "main-password.db"

@Module
internal object MainPasswordDataStoreModule {

    private val Context.protoMainPasswordDataStore by dataStore(
        fileName = MAIN_PASSWORD_DATA_STORE_NAME,
        serializer = MainPasswordSerializer,
    )

    @Single
    @MainPasswordQualifier
    fun provideProtoMainPasswordDataStore(context: Context) = context.protoMainPasswordDataStore
}
