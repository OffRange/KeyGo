package de.davis.keygo.feature.auth.di

import de.davis.keygo.core.identity.di.CoreIdentityModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module

@Module(includes = [CoreIdentityModule::class])
@Configuration
@ComponentScan("de.davis.keygo.feature.auth")
object AuthModule