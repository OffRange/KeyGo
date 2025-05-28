package de.davis.keygo.auth.di

import de.davis.keygo.auth.data.repository.CheckBiometricCapabilityRepositoryImpl
import de.davis.keygo.auth.domain.repository.CheckBiometricCapabilityRepository
import de.davis.keygo.auth.presentation.AuthViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val authModule = module {
    viewModelOf(::AuthViewModel)

    singleOf(::CheckBiometricCapabilityRepositoryImpl) bind CheckBiometricCapabilityRepository::class
}