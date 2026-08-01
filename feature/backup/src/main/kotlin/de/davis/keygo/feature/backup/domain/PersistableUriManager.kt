package de.davis.keygo.feature.backup.domain

import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri

interface PersistableUriManager {

    fun takePersistableUriPermission(uri: BackupDestinationUri)
    fun releasePersistableUriPermission(uri: BackupDestinationUri)
}