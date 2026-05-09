package de.davis.keygo.feature.item.core.domain.model

sealed interface LoginError {
    data object BlankName : LoginError
    data object BlankPassword : LoginError
    data object InvalidVaultId : LoginError
    data object InvalidItemId : LoginError
    data class DatabaseError(val throwable: Throwable) : LoginError
}
