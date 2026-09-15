package de.davis.keygo.core.identity.domain.model

sealed interface UnlockableByBiometricsResult {
    data object Available : UnlockableByBiometricsResult
    data object NoHardware : UnlockableByBiometricsResult
    data object NotEnrolled : UnlockableByBiometricsResult

    data class NoAccount(val hardwareAvailable: Boolean) : UnlockableByBiometricsResult
}

fun UnlockableByBiometricsResult.hasHardware() = when (this) {
    UnlockableByBiometricsResult.NoHardware -> false
    is UnlockableByBiometricsResult.NoAccount -> hardwareAvailable
    else -> true
}
