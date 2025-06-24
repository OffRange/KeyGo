package de.davis.keygo.core.presentation.model

sealed interface InputFieldError {
    data object Empty : InputFieldError
}