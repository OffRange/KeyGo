package de.davis.keygo.feature.password_health.domain.model

sealed interface CheckError {

    data object Unreachable : CheckError
    data object Failed : CheckError
}
