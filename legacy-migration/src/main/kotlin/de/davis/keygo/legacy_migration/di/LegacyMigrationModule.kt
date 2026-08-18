package de.davis.keygo.legacy_migration.di

import de.davis.keygo.legacy_migration.di.annotation.MigrationScopeQualifier
import kotlinx.coroutines.CoroutineScope
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
     * The scope the v1 import runs in. Application-lived, so no screen's lifetime can cut a run
     * short, and a SupervisorJob so a run that fails takes nothing else with it.
     *
     * Deliberately names no dispatcher. Every blocking call under the import switches for itself -
     * `LegacyKeyRepository` for the Keystore, `LegacyItemRepositoryImpl.withDao` for the file, the
     * row loop for the decrypt and parse, `CryptographicScopeImpl` for the re-encryption - so this
     * scope has nothing left to correct. Pinning one here would only hide the next call that
     * forgets to, and there would be no single place left that answers what thread a given
     * operation runs on.
     */
    @Single
    @MigrationScopeQualifier
    fun provideMigrationScope(): CoroutineScope = CoroutineScope(SupervisorJob())
}
