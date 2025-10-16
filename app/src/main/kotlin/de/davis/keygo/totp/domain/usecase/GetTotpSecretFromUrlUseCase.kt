package de.davis.keygo.totp.domain.usecase

import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.totp.domain.model.Algorithm
import de.davis.keygo.totp.domain.model.TotpSecretInformation
import de.davis.keygo.totp.domain.model.TotpSecretUrlParseError
import org.koin.core.annotation.Single
import java.net.URI

@Single
class GetTotpSecretFromUrlUseCase {

    operator fun invoke(url: String): Result<TotpSecretInformation, TotpSecretUrlParseError> {
        if (!url.startsWith("otpauth://"))
            return Result.Failure(
                TotpSecretUrlParseError.SchemeNotSupported(
                    url.split("://").firstOrNull()
                )
            )

        val uri = sanitizeAndParse(url).getOrNull()
            ?: return Result.Failure(TotpSecretUrlParseError.CouldNotParseUrl(url))

        if (uri.host != "totp")
            return Result.Failure(TotpSecretUrlParseError.HostNotSupported(uri.host))

        val path = uri.path?.trimStart('/')
            ?.takeIf { it.isNotBlank() }
            ?: return Result.Failure(TotpSecretUrlParseError.NoPathProvided)

        var (issuer, accountName) = when {
            path.contains(":") -> {
                val parts = path.split(":", limit = 2)
                parts[0] to parts[1]
            }

            else -> {
                null to path
            }
        }

        val query = uri.query?.ifBlank { null }
            ?: return Result.Failure(TotpSecretUrlParseError.NoQueryProvided)
        val algorithm = query.getQueryParameter("algorithm").asAlgorithmOrSHA1()
        val digits = query.getQueryParameter("digits")
            ?.toIntOrNull()
            ?: DefaultTotpValues.DEFAULT_DIGITS
        val period = query.getQueryParameter("period")
            ?.toIntOrNull()
            ?: DefaultTotpValues.DEFAULT_PERIOD
        val secret = query.getQueryParameter("secret")
            ?.takeIf { it.isNotBlank() }
            ?: return Result.Failure(TotpSecretUrlParseError.NoSecretProvided)

        val paramIssuer = query.getQueryParameter("issuer")
        if (issuer != null && paramIssuer != null && issuer != paramIssuer)
            return Result.Failure(TotpSecretUrlParseError.IssuerMismatch(issuer, paramIssuer))

        issuer = paramIssuer ?: issuer

        return TotpSecretInformation(
            secret = secret,
            issuer = issuer,
            accountName = accountName,
            algorithm = algorithm,
            digits = digits,
            period = period
        ).let { Result.Success(it) }
    }

    private fun String.getQueryParameter(parameter: String): String? {
        val regex = Regex("(?:^|[?&])$parameter=([^&#]*)")
        return regex.find(this)?.groupValues?.get(1)
    }

    private fun String?.asAlgorithmOrSHA1() =
        this?.let { Algorithm.fromString(it) } ?: DefaultTotpValues.DEFAULT_ALGORITHM

    private fun sanitizeAndParse(url: String): Result<URI, Unit> {
        // - (?<authority>[^/?#]+)  : Named group capturing one or more characters that are not '/', '?', or '#'
        // - (?<label>[^?#]*)?      : Optional named group capturing zero or more characters that are not '?' or '#'.
        //                            It includes the leading '/' in the path.
        // - (?:\\?(?<query>.*))?   : Non-capturing group capturing the query part after '?' if it exists
        val matches = "^otpauth://(?<authority>[^/?#]+)(?<label>[^?#]*)?(?:\\?(?<query>.*))?$"
            .toRegex()
            .matchEntire(url)
            ?: return Result.Failure(Unit)

        val authority = matches.groups["authority"]?.value
        val label = matches.groups["label"]?.value
        val query = matches.groups["query"]?.value

        return Result.Success(URI("otpauth", authority, label, query, null))
    }
}

object DefaultTotpValues {
    val DEFAULT_ALGORITHM = Algorithm.SHA1
    const val DEFAULT_DIGITS = 6
    const val DEFAULT_PERIOD = 30
}