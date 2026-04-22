package de.davis.keygo.feature.item.core.domain.model

sealed interface PasswordError {
    data object BlankName : PasswordError
    data object BlankPassword : PasswordError
    data object InvalidVaultId : PasswordError
    data object InvalidItemId : PasswordError
    data class DatabaseError(val throwable: Throwable) : PasswordError
}