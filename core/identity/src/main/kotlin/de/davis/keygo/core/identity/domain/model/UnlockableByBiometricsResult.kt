package de.davis.keygo.core.identity.domain.model

sealed interface UnlockableByBiometricsResult {
    data object Available : UnlockableByBiometricsResult
    data object NoHardware : UnlockableByBiometricsResult
    data object NoAccount : UnlockableByBiometricsResult
    data object NotEnrolled : UnlockableByBiometricsResult
}

fun UnlockableByBiometricsResult.hasHardware() = this != UnlockableByBiometricsResult.NoHardware