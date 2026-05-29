package de.davis.keygo.feature.item.core.presentation.model

sealed interface InputFieldError {
    data object Empty : InputFieldError
    data object Invalid : InputFieldError
    data object System : InputFieldError
}
