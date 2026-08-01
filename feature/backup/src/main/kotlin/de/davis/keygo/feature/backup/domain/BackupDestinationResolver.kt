package de.davis.keygo.feature.backup.domain

import de.davis.keygo.feature.backup.domain.model.BackupDestination
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri

interface BackupDestinationResolver {

    /**
     * @param cachedName the destination's name as it was resolved while the grant was live. Given
     * one, the provider is not asked again - it may no longer be willing, or able, to answer.
     */
    suspend fun resolve(
        uri: BackupDestinationUri,
        cachedName: String? = null,
    ): BackupDestination
}