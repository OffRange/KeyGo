package de.davis.keygo.auth.di

import android.content.Context
import androidx.datastore.dataStore
import com.lambdapioneer.argon2kt.Argon2Kt
import de.davis.keygo.auth.data.local.model.ProtoPasswordKeyData
import de.davis.keygo.core.di.DefaultProtoSerializer
import de.davis.keygo.core.di.annotation.PasswordQualifier
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@ComponentScan("de.davis.keygo.auth.**")
object AuthModule {

    private val Context.protoPasswordKeyDataStore by dataStore(
        "password_key_data.pb",
        DefaultProtoSerializer(
            defaultInstance = ProtoPasswordKeyData.getDefaultInstance(),
            parser = ProtoPasswordKeyData.parser()
        )
    )

    @Single
    internal fun provideArgon2Kt() = Argon2Kt()

    @Single
    @PasswordQualifier
    internal fun provideProtoPasswordKeyDataStore(context: Context) =
        context.protoPasswordKeyDataStore
}