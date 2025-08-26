package de.davis.keygo.core.di

import android.content.Context
import androidx.datastore.dataStore
import de.davis.keygo.auth.data.local.model.ProtoBiometricKeyData
import de.davis.keygo.core.di.annotation.BiometricQualifier
import me.gosimple.nbvcxz.Nbvcxz
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import java.security.KeyStore

@Module
@ComponentScan("de.davis.keygo.core.**")
object CoreModule {

    @Single
    internal fun provideKeyStore(): KeyStore =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Single
    internal fun provideNbvcxz() = Nbvcxz()


    private val Context.protoBiometricKeyDataStore by dataStore(
        "biometric_key_data.pb",
        DefaultProtoSerializer(
            defaultInstance = ProtoBiometricKeyData.getDefaultInstance(),
            parser = ProtoBiometricKeyData.parser()
        )
    )

    @Single
    @BiometricQualifier
    internal fun provideProtoBiometricKeyDataStore(context: Context) =
        context.protoBiometricKeyDataStore
}