package de.davis.keygo.auth.presentation.model

sealed interface UIPasswordError {

    data object Incorrect : UIPasswordError
    data object None : UIPasswordError
}