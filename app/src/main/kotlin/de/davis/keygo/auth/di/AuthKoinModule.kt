package de.davis.keygo.auth.di

import de.davis.keygo.auth.data.BiometricManagerImpl
import de.davis.keygo.auth.domain.BiometricManager
import de.davis.keygo.auth.presentation.AuthViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

val authModule = module {
    single { parameter ->
        BiometricManagerImpl(
            androidContext(),
            parameter.get(),
            parameter.get()
        )
    } bind BiometricManager::class
    viewModel { parameter -> AuthViewModel(parameter.get(), get()) }
}