package de.davis.keygo.core.sync.domain.stepup

import de.davis.keygo.core.sync.domain.model.SyncError
import kotlinx.coroutines.flow.StateFlow

interface StepUpPrompter {

    val prompt: StateFlow<StepUpPrompt?>

    fun submit(password: String)
    fun cancel()

    /** Ends any prompt, even mid step-up (e.g. session ended); pending runs return [SyncError.StepUpCancelled]. */
    fun abandon()
}
