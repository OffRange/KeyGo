package de.davis.keygo.core.item.di

import de.davis.keygo.core.item.data.local.datasource.DatabaseModule
import me.gosimple.nbvcxz.Nbvcxz
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module(includes = [DatabaseModule::class])
@Configuration
@ComponentScan("de.davis.keygo.core.item")
object CoreItemModule {

    @Single
    internal fun provideNbvcxz() = Nbvcxz()
}