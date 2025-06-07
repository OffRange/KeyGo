package de.davis.keygo.item.di

import de.davis.keygo.item.data.PasswordGeneratorImpl
import de.davis.keygo.item.domain.PasswordGenerator
import de.davis.keygo.item.domain.usecase.EstimatePasswordStrengthUseCase
import de.davis.keygo.item.presentation.password.GeneratePasswordViewModel
import me.gosimple.nbvcxz.Nbvcxz
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.ksp.generated.module

val itemModule = module {
    //viewModelOf(::PasswordViewModel)
    viewModelOf(::GeneratePasswordViewModel)

    single { Nbvcxz() }
    singleOf(::EstimatePasswordStrengthUseCase)

    singleOf(::PasswordGeneratorImpl) bind PasswordGenerator::class

    includes(ItemModule.module)
}

@Module
@ComponentScan("de.davis.keygo.item.**")
object ItemModule