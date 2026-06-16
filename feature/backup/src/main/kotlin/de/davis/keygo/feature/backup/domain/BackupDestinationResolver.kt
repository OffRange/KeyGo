package de.davis.keygo.feature.backup.domain

import de.davis.keygo.feature.backup.domain.model.BackupDestination
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri

interface BackupDestinationResolver {

    suspend fun resolve(uri: BackupDestinationUri): BackupDestination
}