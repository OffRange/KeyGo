package de.davis.keygo.feature.backup.data

import de.davis.keygo.feature.backup.domain.BackupDestinationResolver
import de.davis.keygo.feature.backup.domain.model.BackupDestination
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri

class FakeBackupDestinationResolver(
    var result: BackupDestination = BackupDestination(
        provider = BackupDestination.Provider.OnDevice,
        displayPath = "Internal storage/Backups",
    ),
) : BackupDestinationResolver {

    var lastUri: BackupDestinationUri? = null

    override suspend fun resolve(uri: BackupDestinationUri): BackupDestination {
        lastUri = uri
        return result
    }
}
