package de.davis.keygo.core.sync.domain.stepup

import de.davis.keygo.core.sync.domain.model.SyncError

data class StepUpPrompt(
    val busy: Boolean = false,
    val error: SyncError? = null,
)
