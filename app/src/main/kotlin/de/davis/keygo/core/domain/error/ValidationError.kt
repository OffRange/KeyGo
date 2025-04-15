package de.davis.keygo.core.domain.error

sealed interface ValidationError {

    data object NoMatch : ValidationError
}