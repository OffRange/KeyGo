package de.davis.keygo.core.sync.domain.model

sealed interface SyncError {

    data object AccountMismatch : SyncError
    data object FreshAuthRequired : SyncError
    data object InvalidCredentials : SyncError

    /** A bug or a broken device dependency, never the user's input. [reason] is for logs only. */
    data class Internal(val reason: String) : SyncError

    /** The server address is not a plain http(s) origin. */
    data class InvalidUrl(val reason: String) : SyncError
    data object InvalidUsername : SyncError
    data object InviteInvalid : SyncError
    data object InviteRequired : SyncError
    data class Network(val kind: NetworkFailure) : SyncError
    data object RateLimited : SyncError

    /** KeyGo answered with one of its own error codes; [code] is the wire name. */
    data class Server(val code: String, val reason: String) : SyncError

    /** Something answered with a body KeyGo cannot use, so likely not a KeyGo server. */
    data class InvalidResponse(val reason: String) : SyncError

    /** A non-KeyGo error body with this HTTP [status]: a reverse proxy, a redirect, a foreign server. */
    data class UnexpectedStatus(val status: Int, val reason: String) : SyncError
    data class SignedOut(val reason: String?) : SyncError
    /** The user dismissed the step-up prompt; callers show nothing. */
    data object StepUpCancelled : SyncError
    data object UsernameTaken : SyncError
    data object VaultLocked : SyncError
}