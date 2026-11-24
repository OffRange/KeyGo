package de.davis.keygo.core.sync

import de.davis.keygo.core.sync.domain.model.SyncError
import de.davis.keygo.core.sync.domain.stepup.StepUpGate
import de.davis.keygo.core.util.Result

class FakeStepUpGate(var cancels: Boolean = false) : StepUpGate {

    var runs = 0
        private set
    var forcedRuns = 0
        private set

    override suspend fun <T> run(
        force: Boolean,
        call: suspend () -> Result<T, SyncError>,
    ): Result<T, SyncError> {
        runs++
        if (force) forcedRuns++
        return if (cancels) Result.Failure(SyncError.StepUpCancelled) else call()
    }
}
