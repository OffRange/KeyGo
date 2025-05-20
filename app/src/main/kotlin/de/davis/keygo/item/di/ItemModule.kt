package de.davis.keygo.item.di

import de.davis.keygo.item.domain.usecase.EstimatePasswordStrengthUseCase
import de.davis.keygo.item.presentation.password.PasswordViewModel
import me.gosimple.nbvcxz.Nbvcxz
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val itemModule = module {
    viewModelOf(::PasswordViewModel)

    single { Nbvcxz() }
    singleOf(::EstimatePasswordStrengthUseCase)
}