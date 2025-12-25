package de.davis.keygo.feature.item.view.di

import de.davis.keygo.feature.item.core.di.FeatureItemCoreModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module


@Module(includes = [FeatureItemCoreModule::class])
@ComponentScan("de.davis.keygo.feature.item.view")
object FeatureItemViewModule