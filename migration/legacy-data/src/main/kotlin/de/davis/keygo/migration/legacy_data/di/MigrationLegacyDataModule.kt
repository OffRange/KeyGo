package de.davis.keygo.migration.legacy_data.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module

@Module
@Configuration
@ComponentScan("de.davis.keygo.migration.legacy_data")
object MigrationLegacyDataModule
