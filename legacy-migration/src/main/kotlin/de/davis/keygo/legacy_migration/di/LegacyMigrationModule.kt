package de.davis.keygo.legacy_migration.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module

@Module(
    includes = [
        LegacyDatabaseModule::class,
        MainPasswordDataStoreModule::class,
    ],
)
@Configuration
@ComponentScan("de.davis.keygo.legacy_migration")
object LegacyMigrationModule
