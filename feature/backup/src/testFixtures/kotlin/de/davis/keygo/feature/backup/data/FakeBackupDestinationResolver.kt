package de.davis.keygo.feature.backup.data

import de.davis.keygo.feature.backup.FakeBackupScheduler
import de.davis.keygo.feature.backup.domain.BackupDestinationResolver
import de.davis.keygo.feature.backup.domain.model.BackupDestination
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import kotlinx.coroutines.CompletableDeferred

/**
 * Records every resolution and hands back [result]. Pass [gate] to park inside [resolve] until the
 * test completes it - mirroring [FakeBackupScheduler] - so a test can hold a resolution open and
 * observe whether anything re-enters it.
 */
class FakeBackupDestinationResolver(
    var result: BackupDestination = BackupDestination(
        provider = BackupDestination.Provider.OnDevice,
        displayPath = "Internal storage/Backups",
    ),
    private val gate: CompletableDeferred<Unit>? = null,
) : BackupDestinationResolver {

    val calls = mutableListOf<BackupDestinationUri>()

    val lastUri: BackupDestinationUri? get() = calls.lastOrNull()
    var lastCachedName: String? = null

    override suspend fun resolve(
        uri: BackupDestinationUri,
        cachedName: String?,
    ): BackupDestination {
        calls += uri
        lastCachedName = cachedName
        gate?.await()
        return result
    }
}
