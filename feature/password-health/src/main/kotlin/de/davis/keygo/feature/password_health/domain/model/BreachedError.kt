package de.davis.keygo.feature.password_health.domain.model

sealed interface BreachedError {

    data object ApiFailed : BreachedError
    data object Unreachable : BreachedError
    data object InvalidPrefix : BreachedError
}
