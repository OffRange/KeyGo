package de.davis.keygo.core.item.di

import de.davis.keygo.core.util.di.CoreUtilModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module(includes = [CoreUtilModule::class])
@ComponentScan("de.davis.keygo.core.item.**")
object CoreItemModule