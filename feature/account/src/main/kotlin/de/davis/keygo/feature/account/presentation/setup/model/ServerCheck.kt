package de.davis.keygo.feature.account.presentation.setup.model

internal sealed interface ServerCheck {

    data object Idle : ServerCheck
    data object Checking : ServerCheck

    data class Found(
        val serverUrl: String,
        val version: String,
        val publicUrl: String,
        val inviteOnly: Boolean,
    ) : ServerCheck

    data class Failed(val reason: Reason) : ServerCheck {

        val canRetry: Boolean
            get() = reason != Reason.InvalidAddress

        enum class Reason {
            InvalidAddress,
            UnknownHost,
            Timeout,
            Refused,
            Unreachable,
            InsecureConnection,
            UntrustedCertificate,
            SecureConnectionFailed,
            Redirected,
            NotKeyGo,
            ServerUnavailable,
            ServerError,
            RateLimited,
            Unknown,
        }
    }
}
