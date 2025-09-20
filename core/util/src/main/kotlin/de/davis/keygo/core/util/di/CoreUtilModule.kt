package de.davis.keygo.core.util.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module
@ComponentScan("de.davis.keygo.core.util.**")
object CoreUtilModule