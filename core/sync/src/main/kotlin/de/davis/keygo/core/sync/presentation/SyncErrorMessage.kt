package de.davis.keygo.core.sync.presentation

import de.davis.keygo.core.sync.R
import de.davis.keygo.core.sync.domain.model.NetworkFailure
import de.davis.keygo.core.sync.domain.model.SyncError

val SyncError.messageRes: Int
    get() = when (this) {
        is SyncError.Network -> kind.messageRes
        is SyncError.InvalidResponse -> R.string.sync_error_not_keygo
        is SyncError.UnexpectedStatus -> statusMessageRes(status)
        is SyncError.Server -> R.string.sync_error_server
        is SyncError.InvalidUrl -> R.string.sync_error_invalid_address
        SyncError.RateLimited -> R.string.sync_error_rate_limited
        SyncError.UsernameTaken -> R.string.sync_error_username_taken
        SyncError.InvalidUsername -> R.string.sync_error_username_rejected
        SyncError.InviteInvalid -> R.string.sync_error_invite_invalid
        SyncError.InviteRequired -> R.string.sync_error_invite_required
        SyncError.InvalidCredentials -> R.string.sync_error_invalid_credentials
        is SyncError.SignedOut -> R.string.sync_error_signed_out
        SyncError.FreshAuthRequired,
        SyncError.StepUpCancelled,
            -> R.string.sync_error_fresh_auth_required
        SyncError.AccountMismatch -> R.string.sync_error_account_mismatch
        SyncError.VaultLocked -> R.string.sync_error_vault_locked
        // A broken keystore must not read as a typo in the address.
        is SyncError.Internal -> R.string.sync_error_unknown
    }

private val NetworkFailure.messageRes: Int
    get() = when (this) {
        NetworkFailure.UnknownHost -> R.string.sync_error_unknown_host
        NetworkFailure.Timeout -> R.string.sync_error_timeout
        NetworkFailure.Refused -> R.string.sync_error_refused
        NetworkFailure.Unreachable, NetworkFailure.Other -> R.string.sync_error_unreachable
        NetworkFailure.CleartextBlocked -> R.string.sync_error_insecure
        NetworkFailure.UntrustedCertificate -> R.string.sync_error_untrusted_certificate
        NetworkFailure.Tls -> R.string.sync_error_tls_failed
    }

private fun statusMessageRes(status: Int): Int = when (status) {
    in 300..399 -> R.string.sync_error_redirected
    // What a reverse proxy answers while the KeyGo container behind it is down.
    502, 503, 504 -> R.string.sync_error_unavailable
    in 500..599 -> R.string.sync_error_server
    else -> R.string.sync_error_not_keygo
}
