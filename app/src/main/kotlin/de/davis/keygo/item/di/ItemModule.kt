package de.davis.keygo.item.di

import de.davis.keygo.item.data.PasswordGeneratorImpl
import de.davis.keygo.item.domain.PasswordGenerator
import de.davis.keygo.item.domain.usecase.CreateNewPassword
import de.davis.keygo.item.domain.usecase.EstimatePasswordStrengthUseCase
import de.davis.keygo.item.presentation.password.GeneratePasswordViewModel
import de.davis.keygo.item.presentation.password.PasswordViewModel
import me.gosimple.nbvcxz.Nbvcxz
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val itemModule = module {
    viewModelOf(::PasswordViewModel)
    viewModelOf(::GeneratePasswordViewModel)

    single { Nbvcxz() }
    singleOf(::EstimatePasswordStrengthUseCase)

    singleOf(::PasswordGeneratorImpl) bind PasswordGenerator::class

    singleOf(::CreateNewPassword)
}