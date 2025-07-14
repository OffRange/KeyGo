package de.davis.keygo.totp.domain.usecase

import de.davis.keygo.core.domain.Result
import de.davis.keygo.totp.domain.model.Algorithm
import de.davis.keygo.totp.domain.model.TotpSecretInformation
import de.davis.keygo.totp.domain.model.TotpSecretUrlParseError
import org.koin.core.annotation.Single
import java.net.URI

@Single
class GetTotpSecretFromUrlUseCase {

    operator fun invoke(url: String): Result<TotpSecretInformation, TotpSecretUrlParseError> {
        val uri = URI.create(url)
        if (uri.scheme != "otpauth")
            return Result.Failure(TotpSecretUrlParseError.SchemeNotSupported(uri.scheme))

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

        val query = uri.query ?: return Result.Failure(TotpSecretUrlParseError.NoQueryProvided)
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
}

object DefaultTotpValues {
    val DEFAULT_ALGORITHM = Algorithm.SHA1
    const val DEFAULT_DIGITS = 6
    const val DEFAULT_PERIOD = 30
}