package de.davis.keygo.feature.backup

import de.davis.keygo.feature.backup.domain.PersistableUriManager
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri

class FakePersistableUriManager : PersistableUriManager {

    val taken = mutableListOf<BackupDestinationUri>()
    val released = mutableListOf<BackupDestinationUri>()
    var throwOnTake: Throwable? = null

    override fun takePersistableUriPermission(uri: BackupDestinationUri) {
        throwOnTake?.let { throw it }
        taken += uri
    }

    override fun releasePersistableUriPermission(uri: BackupDestinationUri) {
        released += uri
    }
}
