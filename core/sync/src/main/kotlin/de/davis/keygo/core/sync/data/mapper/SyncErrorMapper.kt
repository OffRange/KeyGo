package de.davis.keygo.core.sync.data.mapper

import de.davis.keygo.core.sync.domain.model.NetworkFailure
import de.davis.keygo.core.sync.domain.model.SyncError
import de.davisalessandro.keygo.rust.NetworkException
import de.davisalessandro.keygo.rust.SyncException

internal fun SyncException.toSyncError() = when (this) {
    is SyncException.AccountMismatch -> SyncError.AccountMismatch
    is SyncException.FreshAuthRequired -> SyncError.FreshAuthRequired
    is SyncException.InvalidCredentials -> SyncError.InvalidCredentials
    is SyncException.Internal -> SyncError.Internal(reason)
    is SyncException.InvalidUrl -> SyncError.InvalidUrl(reason)
    is SyncException.InvalidUsername -> SyncError.InvalidUsername
    is SyncException.InviteInvalid -> SyncError.InviteInvalid
    is SyncException.InviteRequired -> SyncError.InviteRequired
    is SyncException.Network -> SyncError.Network(kind.toDomain())
    is SyncException.RateLimited -> SyncError.RateLimited
    is SyncException.Server -> SyncError.Server(code, reason)
    is SyncException.InvalidResponse -> SyncError.InvalidResponse(reason)
    is SyncException.UnexpectedStatus -> SyncError.UnexpectedStatus(status.toInt(), reason)
    is SyncException.SignedOut -> SyncError.SignedOut(reason)
    is SyncException.UsernameTaken -> SyncError.UsernameTaken
    is SyncException.VaultLocked -> SyncError.VaultLocked
}

private fun NetworkException.toDomain() = when (this) {
    is NetworkException.UnknownHost -> NetworkFailure.UnknownHost
    is NetworkException.Timeout -> NetworkFailure.Timeout
    is NetworkException.Refused -> NetworkFailure.Refused
    is NetworkException.Unreachable -> NetworkFailure.Unreachable
    is NetworkException.CleartextBlocked -> NetworkFailure.CleartextBlocked
    is NetworkException.UntrustedCertificate -> NetworkFailure.UntrustedCertificate
    is NetworkException.Tls -> NetworkFailure.Tls
    is NetworkException.Other -> NetworkFailure.Other
}
