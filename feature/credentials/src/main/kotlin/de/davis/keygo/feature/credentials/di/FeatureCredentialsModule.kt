package de.davis.keygo.feature.credentials.di

import de.davis.keygo.core.security.di.CoreSecurityModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module(includes = [CoreSecurityModule::class])
@ComponentScan("de.davis.keygo.feature.credentials.**")
object FeatureCredentialsModule