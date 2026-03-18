package de.davis.keygo.feature.totp.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module

@Module
@Configuration
@ComponentScan("de.davis.keygo.feature.totp")
object FeatureTotpModule