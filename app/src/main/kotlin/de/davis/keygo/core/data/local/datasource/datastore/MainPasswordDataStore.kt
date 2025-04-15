package de.davis.keygo.core.data.local.datasource.datastore

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.dataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private const val DATA_STORE_NAME = "main-password.db"

private fun <T> produceMigrations(context: Context): List<DataMigration<T>> = emptyList()

internal val dataStoreModule = module {
    single {
        val appContext = androidContext()
        DataStoreFactory.create(
            storage = OkioStorage(
                FileSystem.SYSTEM,
                OkioSerializerWrapper(MainPasswordSerializer)
            ) {
                appContext.dataStoreFile(DATA_STORE_NAME).absolutePath.toPath()
            },
            corruptionHandler = null,
            migrations = produceMigrations(appContext),
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        )
    }
}

internal class OkioSerializerWrapper<T>(private val delegate: Serializer<T>) : OkioSerializer<T> {
    override val defaultValue: T
        get() = delegate.defaultValue

    override suspend fun readFrom(source: BufferedSource): T {
        return delegate.readFrom(source.inputStream())
    }

    override suspend fun writeTo(t: T, sink: BufferedSink) {
        delegate.writeTo(t, sink.outputStream())
    }
}