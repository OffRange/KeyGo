package de.davis.keygo.core.security.domain.model

sealed interface BiometricString {

    sealed interface Title : BiometricString {
        data object Authenticate : Title
        data class UnlockItem(val itemName: String) : Title
    }

    sealed interface NegativeButton : BiometricString {
        data object Cancel : NegativeButton
        data object Password : NegativeButton
    }
}