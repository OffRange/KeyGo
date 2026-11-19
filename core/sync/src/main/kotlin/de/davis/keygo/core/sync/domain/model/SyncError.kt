package de.davis.keygo.core.sync.domain.model

sealed interface SyncError {

    data object AccountMismatch : SyncError
    data class Client(val reason: String) : SyncError
    data object FreshAuthRequired : SyncError
    data object InvalidCredentials : SyncError
    data class InvalidUrl(val reason: String) : SyncError
    data object InvalidUsername : SyncError
    data object InviteInvalid : SyncError
    data object InviteRequired : SyncError
    data class Network(val reason: String) : SyncError
    data object RateLimited : SyncError
    data class Server(val code: String, val reason: String) : SyncError
    data class SignedOut(val reason: String?) : SyncError
    data object UsernameTaken : SyncError
    data object VaultLocked : SyncError
}