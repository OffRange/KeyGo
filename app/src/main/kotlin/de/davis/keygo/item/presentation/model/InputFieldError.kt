package de.davis.keygo.item.presentation.model

sealed interface InputFieldError {
    data object Empty : InputFieldError
}