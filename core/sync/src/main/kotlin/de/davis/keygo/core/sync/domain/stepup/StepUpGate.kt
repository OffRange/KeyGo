package de.davis.keygo.core.sync.domain.stepup

import de.davis.keygo.core.sync.domain.model.SyncError
import de.davis.keygo.core.util.Result

interface StepUpGate {

    suspend fun <T> run(
        force: Boolean = false,
        call: suspend () -> Result<T, SyncError>,
    ): Result<T, SyncError>
}
