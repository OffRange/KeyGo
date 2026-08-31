package de.davis.keygo.feature.backup.presentation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import de.davis.keygo.feature.backup.presentation.export.ExportWizardScreen
import de.davis.keygo.feature.backup.presentation.hub.BackupHubScreen
import de.davis.keygo.feature.backup.presentation.import.ImportWizardScreen

/** The backup screens are their own flow on top of settings, so they share one [metadata] set. */
fun EntryProviderScope<NavKey>.backupEntries(
    metadata: Map<String, Any> = emptyMap(),
    navigateToDestination: (NavKey) -> Unit,
    navigateUp: () -> Unit,
) {
    entry<BackupHubRoute>(metadata = metadata) {
        BackupHubScreen(
            navigateToExport = { navigateToDestination(BackupExportRoute) },
            navigateToImport = { navigateToDestination(BackupImportRoute) },
        )
    }

    entry<BackupExportRoute>(metadata = metadata) {
        ExportWizardScreen(navigateUp = navigateUp)
    }

    entry<BackupImportRoute>(metadata = metadata) {
        ImportWizardScreen(navigateUp = navigateUp)
    }
}
