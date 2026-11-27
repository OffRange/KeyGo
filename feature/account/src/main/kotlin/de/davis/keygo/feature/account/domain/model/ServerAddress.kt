package de.davis.keygo.feature.account.domain.model

import de.davis.keygo.core.util.Result
import java.net.URI
import java.net.URISyntaxException

internal enum class ServerAddressError {
    Empty,
    Invalid,

    NotAnOrigin, // TODO: allow a server address with a path e.g. example.com/vault
}

@JvmInline
internal value class ServerAddress private constructor(val origin: String) {

    companion object {

        fun parse(input: String): Result<ServerAddress, ServerAddressError> {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return Result.Failure(ServerAddressError.Empty)

            val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
            val uri = try {
                URI(withScheme)
            } catch (_: URISyntaxException) {
                return Result.Failure(ServerAddressError.Invalid)
            }

            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase()
            val isValid = (scheme == "https" || scheme == "http") &&
                    !host.isNullOrEmpty() &&
                    uri.rawUserInfo == null &&
                    (uri.port == -1 || uri.port in 1..65_535)
            if (!isValid) return Result.Failure(ServerAddressError.Invalid)

            // Same rule as Rust's check_base_url: an origin, nothing after it.
            val isOrigin = (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") &&
                    uri.rawQuery == null && uri.rawFragment == null
            if (!isOrigin) return Result.Failure(ServerAddressError.NotAnOrigin)

            // A default port is dropped, the way Rust's `Url` prints the origin it stores.
            val port = if (uri.port == -1 || uri.port == defaultPort(scheme)) "" else ":${uri.port}"
            return Result.Success(ServerAddress("$scheme://$host$port"))
        }

        private fun defaultPort(scheme: String) = if (scheme == "https") 443 else 80
    }
}
