package de.davis.keygo.core.security.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module
@ComponentScan("de.davis.keygo.core.security.**")
object CoreSecurityModule