package de.davis.keygo.core.sync.domain.model

enum class NetworkFailure {
    UnknownHost,
    Timeout,
    Refused,
    Unreachable,
    CleartextBlocked,
    UntrustedCertificate,
    Tls,
    Other,
}
