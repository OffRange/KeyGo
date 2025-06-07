package de.davis.keygo.item.di

import me.gosimple.nbvcxz.Nbvcxz
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@ComponentScan("de.davis.keygo.item.**")
object ItemModule {

    @Single
    fun provideNbvcxz() = Nbvcxz()
}