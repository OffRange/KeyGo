package de.davis.keygo.migration.legacy_data.domain.usecase

import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.migration.legacy_data.domain.repository.LegacyDatabaseState
import de.davis.keygo.migration.legacy_data.domain.repository.LegacyItemRepository
import org.koin.core.annotation.Single

/**
 * True when v1 data is still on disk waiting to be imported.
 *
 * Exposed so a Settings surface can offer a manual retry later. The migration itself does not need
 * it: it re-probes on every unlock.
 */
@Single
class HasLegacyDataUseCase internal constructor(
    private val legacyItemRepository: LegacyItemRepository,
) {

    /**
     * Only [LegacyDatabaseState.Present] can hold rows to import, so the other three answer false.
     * That includes [LegacyDatabaseState.Unreadable]: there may well be data in a file that will
     * not open, but no retry can reach it, and offering one would promise something we cannot do.
     *
     * A count that fails answers false for the same reason. It is not evidence of an empty file, it
     * is the absence of evidence either way, and this question only ever offers the user a retry.
     */
    suspend operator fun invoke(): Boolean =
        legacyItemRepository.state() == LegacyDatabaseState.Present &&
            (legacyItemRepository.remainingCount().getOrNull() ?: 0) > 0
}
