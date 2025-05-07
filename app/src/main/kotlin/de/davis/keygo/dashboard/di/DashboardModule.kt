package de.davis.keygo.dashboard.di

import de.davis.keygo.dashboard.domain.usecase.FilterUseCase
import de.davis.keygo.dashboard.presentation.DashboardViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val dashboardModule = module {
    singleOf(::FilterUseCase)

    viewModelOf(::DashboardViewModel)
}