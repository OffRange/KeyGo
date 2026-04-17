package de.davis.keygo.core.identity.di

import android.content.Context
import androidx.datastore.dataStore
import de.davis.keygo.core.identity.data.local.model.ProtoAccount
import de.davis.keygo.core.identity.data.local.model.ProtoBiometricKeyData
import de.davis.keygo.core.identity.data.local.model.ProtoPasswordKeyData
import de.davis.keygo.core.identity.di.annotation.AccountRegistryQualifier
import de.davis.keygo.core.security.di.CoreSecurityModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module(includes = [CoreSecurityModule::class])
@Configuration
@ComponentScan("de.davis.keygo.core.identity")
object CoreIdentityModule {

    private val Context.protoBiometricKeyDataStore by dataStore(
        "biometric_key_data.pb",
        DefaultProtoSerializer(
            defaultInstance = ProtoBiometricKeyData.getDefaultInstance(),
            parser = ProtoBiometricKeyData.parser()
        )
    )

    private val Context.protoPasswordKeyDataStore by dataStore(
        "password_key_data.pb",
        DefaultProtoSerializer(
            defaultInstance = ProtoPasswordKeyData.getDefaultInstance(),
            parser = ProtoPasswordKeyData.parser()
        )
    )

    private val Context.protoAccountDataStore by dataStore(
        "account_registry.pb",
        DefaultProtoSerializer(
            defaultInstance = ProtoAccount.getDefaultInstance(),
            parser = ProtoAccount.parser()
        )
    )

    @Single
    @AccountRegistryQualifier
    internal fun provideAccountDataStore(context: Context) =
        context.protoAccountDataStore
}