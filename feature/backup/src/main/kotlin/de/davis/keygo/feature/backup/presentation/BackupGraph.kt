package de.davis.keygo.feature.backup.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import de.davis.keygo.feature.backup.presentation.export.ExportWizardScreen
import de.davis.keygo.feature.backup.presentation.hub.BackupHubScreen
import de.davis.keygo.feature.backup.presentation.import.ImportWizardScreen

fun NavGraphBuilder.backupGraph(
    navigateToDestination: (Any) -> Unit,
    navigateUp: () -> Unit,
) {
    composable<BackupHubRoute> {
        BackupHubScreen(
            navigateToExport = {
                navigateToDestination(BackupExportRoute)
            },
            navigateToImport = {
                navigateToDestination(BackupImportRoute)
            },
        )
    }

    composable<BackupExportRoute> {
        ExportWizardScreen(navigateUp = navigateUp)
    }

    composable<BackupImportRoute> {
        ImportWizardScreen(navigateUp = navigateUp)
    }
}