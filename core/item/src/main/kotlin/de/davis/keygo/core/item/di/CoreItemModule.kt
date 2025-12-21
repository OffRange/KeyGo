package de.davis.keygo.core.item.di

import de.davis.keygo.core.util.di.CoreUtilModule
import me.gosimple.nbvcxz.Nbvcxz
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module(includes = [CoreUtilModule::class])
@ComponentScan("de.davis.keygo.core.item.**")
object CoreItemModule {

    @Single
    internal fun provideNbvcxz() = Nbvcxz()
}