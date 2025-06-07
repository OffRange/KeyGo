package de.davis.keygo.auth.di

import android.content.Context
import androidx.datastore.dataStore
import com.lambdapioneer.argon2kt.Argon2Kt
import de.davis.keygo.auth.data.factory.CipherFactoryImpl
import de.davis.keygo.auth.data.local.model.ProtoBiometricKeyData
import de.davis.keygo.auth.data.local.model.ProtoPasswordKeyData
import de.davis.keygo.auth.data.repository.BiometricAvailabilityRepositoryImpl
import de.davis.keygo.auth.data.repository.BiometricKekRepositoryImpl
import de.davis.keygo.auth.data.repository.KeyDerivationRepositoryImpl
import de.davis.keygo.auth.di.annotation.BiometricQualifier
import de.davis.keygo.auth.domain.factory.CipherFactory
import de.davis.keygo.auth.domain.repository.BiometricAvailabilityRepository
import de.davis.keygo.auth.domain.repository.BiometricKekRepository
import de.davis.keygo.auth.domain.repository.KeyDerivationRepository
import de.davis.keygo.core.di.DefaultProtoSerializer
import de.davis.keygo.core.di.annotation.PasswordQualifier
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.ksp.generated.module

private val Context.protoPasswordKeyDataStore by dataStore(
    "password_key_data.pb",
    DefaultProtoSerializer(
        defaultInstance = ProtoPasswordKeyData.getDefaultInstance(),
        parser = ProtoPasswordKeyData.parser()
    )
)

private val Context.protoBiometricKeyDataStore by dataStore(
    "biometric_key_data.pb",
    DefaultProtoSerializer(
        defaultInstance = ProtoBiometricKeyData.getDefaultInstance(),
        parser = ProtoBiometricKeyData.parser()
    )
)

private val BIOMETRIC_QUALIFIER = named<BiometricQualifier>()
private val PASSWORD_QUALIFIER = named<PasswordQualifier>()

val authModule = module {
    includes(AuthModule.module)

    single(qualifier = PASSWORD_QUALIFIER) { androidContext().protoPasswordKeyDataStore }
    single(qualifier = BIOMETRIC_QUALIFIER) { androidContext().protoBiometricKeyDataStore }

    singleOf(::BiometricAvailabilityRepositoryImpl) bind BiometricAvailabilityRepository::class
    singleOf(::CipherFactoryImpl) bind CipherFactory::class
    singleOf(::BiometricKekRepositoryImpl) bind BiometricKekRepository::class

    single { Argon2Kt() }
    singleOf(::KeyDerivationRepositoryImpl) bind KeyDerivationRepository::class
}