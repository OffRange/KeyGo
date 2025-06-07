package de.davis.keygo.dashboard.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module
@ComponentScan("de.davis.keygo.dashboard.**")
object DashboardModule