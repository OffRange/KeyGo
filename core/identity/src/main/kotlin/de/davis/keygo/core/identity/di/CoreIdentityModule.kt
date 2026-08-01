package de.davis.keygo.core.identity.di

import android.content.Context
import androidx.datastore.dataStore
import de.davis.keygo.core.identity.data.local.model.ProtoAccountState
import de.davis.keygo.core.identity.di.annotation.AccountRegistryQualifier
import de.davis.keygo.core.security.di.CoreSecurityModule
import de.davis.keygo.core.util.data.serializer.DefaultProtoSerializer
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module(includes = [CoreSecurityModule::class])
@Configuration
@ComponentScan("de.davis.keygo.core.identity")
object CoreIdentityModule {
    private val Context.protoAccountStateDataStore by dataStore(
        "account_state.pb",
        DefaultProtoSerializer(
            defaultInstance = ProtoAccountState.getDefaultInstance(),
            parser = ProtoAccountState.parser()
        )
    )

    @Single
    @AccountRegistryQualifier
    internal fun provideAccountDataStore(context: Context) =
        context.protoAccountStateDataStore
}