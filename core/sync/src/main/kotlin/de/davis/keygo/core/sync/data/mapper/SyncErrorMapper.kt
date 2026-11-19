package de.davis.keygo.core.sync.data.mapper

import de.davis.keygo.core.sync.domain.model.SyncError
import de.davisalessandro.keygo.rust.SyncException

internal fun SyncException.toSyncError() = when (this) {
    is SyncException.AccountMismatch -> SyncError.AccountMismatch
    is SyncException.Client -> SyncError.Client(reason)
    is SyncException.FreshAuthRequired -> SyncError.FreshAuthRequired
    is SyncException.InvalidCredentials -> SyncError.InvalidCredentials
    is SyncException.InvalidUrl -> SyncError.InvalidUrl(reason)
    is SyncException.InvalidUsername -> SyncError.InvalidUsername
    is SyncException.InviteInvalid -> SyncError.InviteInvalid
    is SyncException.InviteRequired -> SyncError.InviteRequired
    is SyncException.Network -> SyncError.Network(reason)
    is SyncException.RateLimited -> SyncError.RateLimited
    is SyncException.Server -> SyncError.Server(code, reason)
    is SyncException.SignedOut -> SyncError.SignedOut(reason)
    is SyncException.UsernameTaken -> SyncError.UsernameTaken
    is SyncException.VaultLocked -> SyncError.VaultLocked
}