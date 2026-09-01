package de.davis.keygo.feature.backup.presentation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
object BackupHubRoute : NavKey

@Serializable
object BackupExportRoute : NavKey

@Serializable
object BackupImportRoute : NavKey
