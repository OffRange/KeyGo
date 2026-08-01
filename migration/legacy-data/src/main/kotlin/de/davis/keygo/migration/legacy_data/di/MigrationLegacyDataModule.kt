package de.davis.keygo.migration.legacy_data.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module

@Module(includes = [LegacyDatabaseModule::class])
@Configuration
@ComponentScan("de.davis.keygo.migration.legacy_data")
object MigrationLegacyDataModule
