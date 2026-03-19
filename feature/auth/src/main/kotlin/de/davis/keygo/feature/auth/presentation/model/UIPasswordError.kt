package de.davis.keygo.feature.auth.presentation.model

sealed interface UIPasswordError {

    data object Incorrect : UIPasswordError
    data object Empty : UIPasswordError
    data object None : UIPasswordError
}