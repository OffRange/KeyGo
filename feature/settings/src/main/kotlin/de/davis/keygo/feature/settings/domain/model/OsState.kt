package de.davis.keygo.feature.settings.domain.model

/** Snapshot of OS-owned settings that can only be polled, not observed. */
data class OsState(
    val autofillEnabled: Boolean = false,
    val biometricsAvailable: Boolean = false,
)
