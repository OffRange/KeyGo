package de.davis.keygo.core.identity.di

import android.content.Context
import androidx.datastore.dataStore
import com.lambdapioneer.argon2kt.Argon2Kt
import de.davis.keygo.core.identity.data.local.model.ProtoBiometricKeyData
import de.davis.keygo.core.identity.data.local.model.ProtoPasswordKeyData
import de.davis.keygo.core.identity.di.annotation.BiometricQualifier
import de.davis.keygo.core.identity.di.annotation.PasswordQualifier
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

    @Single
    @BiometricQualifier
    internal fun provideBiometricKeyDataStore(context: Context) = context.protoBiometricKeyDataStore

    @Single
    @PasswordQualifier
    internal fun providePasswordKeyDataStore(context: Context) = context.protoPasswordKeyDataStore


    @Single
    internal fun provideArgon2Kt() = Argon2Kt()
}