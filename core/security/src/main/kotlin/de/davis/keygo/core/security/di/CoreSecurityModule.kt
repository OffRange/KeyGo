package de.davis.keygo.core.security.di

import android.content.Context
import androidx.datastore.dataStore
import de.davis.keygo.core.security.data.local.model.ProtoLockInfo
import de.davis.keygo.core.security.di.annotation.LockInfoQualifier
import de.davis.keygo.core.util.data.serializer.DefaultProtoSerializer
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@Configuration
@ComponentScan("de.davis.keygo.core.security")
object CoreSecurityModule {

    private val Context.protoLockInfoDataStore by dataStore(
        "lock_info.pb",
        DefaultProtoSerializer(
            defaultInstance = ProtoLockInfo.getDefaultInstance(),
            parser = ProtoLockInfo.parser()
        )
    )

    @Single
    @LockInfoQualifier
    internal fun provideLockInfoDataStore(context: Context) =
        context.protoLockInfoDataStore
}
