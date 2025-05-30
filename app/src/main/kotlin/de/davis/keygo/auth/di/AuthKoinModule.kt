package de.davis.keygo.auth.di

import de.davis.keygo.auth.data.factory.BiometricCipherFactoryImpl
import de.davis.keygo.auth.data.repository.BiometricAvailabilityRepositoryImpl
import de.davis.keygo.auth.data.repository.BiometricKekRepositoryImpl
import de.davis.keygo.auth.data.repository.BiometricWrappedKeyRepositoryImpl
import de.davis.keygo.auth.domain.factory.BiometricCipherFactory
import de.davis.keygo.auth.domain.repository.BiometricAvailabilityRepository
import de.davis.keygo.auth.domain.repository.BiometricKekRepository
import de.davis.keygo.auth.domain.repository.BiometricWrappedKeyRepository
import de.davis.keygo.auth.domain.usecase.GetBiometricAvailabilityUseCase
import de.davis.keygo.auth.domain.usecase.PrepareBiometricCipherUseCase
import de.davis.keygo.auth.domain.usecase.UnlockWithBiometricsUseCase
import de.davis.keygo.auth.presentation.AuthViewModel
import de.davis.keygo.core.di.sessionScope
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val authModule = module {
    sessionScope {
        viewModelOf(::AuthViewModel)
    }

    singleOf(::BiometricAvailabilityRepositoryImpl) bind BiometricAvailabilityRepository::class
    singleOf(::BiometricWrappedKeyRepositoryImpl) bind BiometricWrappedKeyRepository::class
    singleOf(::BiometricCipherFactoryImpl) bind BiometricCipherFactory::class
    singleOf(::BiometricKekRepositoryImpl) bind BiometricKekRepository::class
    singleOf(::UnlockWithBiometricsUseCase)

    singleOf(::PrepareBiometricCipherUseCase)
    singleOf(::GetBiometricAvailabilityUseCase)
}