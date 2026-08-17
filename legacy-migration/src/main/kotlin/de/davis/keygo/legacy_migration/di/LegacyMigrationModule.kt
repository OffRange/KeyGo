package de.davis.keygo.legacy_migration.di

import de.davis.keygo.legacy_migration.di.annotation.MigrationScopeQualifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module(
    includes = [
        LegacyDatabaseModule::class,
        MainPasswordDataStoreModule::class,
    ],
)
@Configuration
@ComponentScan("de.davis.keygo.legacy_migration")
object LegacyMigrationModule {

    /**
     * The scope the v1 import runs in.
     *
     * Application-lived, so no screen's lifetime can cut a run short, and on [Dispatchers.IO],
     * because opening the inherited database, reaching the Keystore per row and writing every item
     * back is blocking work throughout. A SupervisorJob so a run that fails takes nothing else with
     * it.
     */
    @Single
    @MigrationScopeQualifier
    fun provideMigrationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
