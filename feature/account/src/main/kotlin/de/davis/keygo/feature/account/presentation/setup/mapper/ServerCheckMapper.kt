package de.davis.keygo.feature.account.presentation.setup.mapper

import de.davis.keygo.core.sync.domain.model.NetworkFailure
import de.davis.keygo.core.sync.domain.model.ServerInfo
import de.davis.keygo.core.sync.domain.model.SyncError
import de.davis.keygo.feature.account.domain.model.ServerAddress
import de.davis.keygo.feature.account.domain.model.ServerAddressError
import de.davis.keygo.feature.account.presentation.setup.model.ServerCheck
import de.davis.keygo.feature.account.presentation.setup.model.ServerCheck.Failed.Reason

internal fun ServerInfo.toFound(address: ServerAddress) = ServerCheck.Found(
    serverUrl = address.origin,
    version = version,
    publicUrl = publicUrl,
    inviteOnly = !allowSignups,
)

internal fun ServerAddressError.toServerCheck(): ServerCheck = when (this) {
    ServerAddressError.Empty -> ServerCheck.Idle
    ServerAddressError.Invalid -> ServerCheck.Failed(Reason.InvalidAddress)
}

internal fun SyncError.toFailureReason(): Reason = when (this) {
    is SyncError.Network -> kind.toFailureReason()
    is SyncError.InvalidResponse -> Reason.NotKeyGo
    is SyncError.UnexpectedStatus -> statusFailureReason(status)
    is SyncError.Server -> Reason.ServerError
    is SyncError.InvalidUrl -> Reason.InvalidAddress
    SyncError.RateLimited -> Reason.RateLimited
    else -> Reason.Unknown
}

private fun NetworkFailure.toFailureReason() = when (this) {
    NetworkFailure.UnknownHost -> Reason.UnknownHost
    NetworkFailure.Timeout -> Reason.Timeout
    NetworkFailure.Refused -> Reason.Refused
    NetworkFailure.Unreachable, NetworkFailure.Other -> Reason.Unreachable
    NetworkFailure.CleartextBlocked -> Reason.InsecureConnection
    NetworkFailure.UntrustedCertificate -> Reason.UntrustedCertificate
    NetworkFailure.Tls -> Reason.SecureConnectionFailed
}

private fun statusFailureReason(status: Int) = when (status) {
    in 300..399 -> Reason.Redirected
    // What a reverse proxy answers while the KeyGo container behind it is down.
    502, 503, 504 -> Reason.ServerUnavailable
    in 500..599 -> Reason.ServerError
    else -> Reason.NotKeyGo
}
