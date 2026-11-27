package de.davis.keygo.core.sync.di

import android.content.Context
import androidx.datastore.dataStore
import de.davis.keygo.core.sync.data.OkHttpTransport
import de.davis.keygo.core.sync.data.local.model.ProtoSessionStore
import de.davis.keygo.core.sync.di.annotation.SessionStoreQualifier
import de.davis.keygo.core.util.data.serializer.DefaultProtoSerializer
import de.davisalessandro.keygo.rust.HttpTransport
import okhttp3.OkHttpClient
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@Configuration
@ComponentScan("de.davis.keygo.core.sync")
object CoreSyncModule {


    private val Context.protoSessionStoreDataStore by dataStore(
        "session_store.pb",
        DefaultProtoSerializer(
            defaultInstance = ProtoSessionStore.getDefaultInstance(),
            parser = ProtoSessionStore.parser()
        )
    )

    @Single
    @SessionStoreQualifier
    internal fun provideSessionStoreDataStore(context: Context) =
        context.protoSessionStoreDataStore

    @Single
    internal fun provideHttpTransport(): HttpTransport = OkHttpTransport(OkHttpClient())
}
