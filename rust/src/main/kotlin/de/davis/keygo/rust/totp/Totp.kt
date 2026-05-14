package de.davis.keygo.rust.totp

import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.resultBinding
import de.davisalessandro.keygo.rust.Algorithm
import de.davisalessandro.keygo.rust.TotpException
import de.davisalessandro.keygo.rust.TotpInfo
import de.davisalessandro.keygo.rust.TotpServiceInterface

typealias TotpService = TotpServiceInterface

fun Algorithm.Companion.fromString(value: String): Result<Algorithm, TotpException> =
    when (value.trim().lowercase()) {
        "sha1", "sha-1" -> Result.Success(Algorithm.SHA1)
        "sha256", "sha-256" -> Result.Success(Algorithm.SHA256)
        "sha512", "sha-512" -> Result.Success(Algorithm.SHA512)
        else -> Result.Failure(TotpException.Generic("unsupported algorithm: $value"))
    }

fun TotpServiceInterface.getTotpWithResult(
    algorithm: Algorithm,
    digits: Int,
    step: Int,
    secret: String,
): Result<String, TotpException> = runCatching { getTotp(algorithm, digits, step, secret) }
    .fold(
        onSuccess = { Result.Success(it) },
        onFailure = { Result.Failure(it as TotpException) }
    )

fun TotpServiceInterface.getUrlWithResult(
    algorithm: String,
    digits: Int,
    step: Int,
    secret: String,
    issuer: String?,
    accountName: String
): Result<String, TotpException> = resultBinding {
    val algo = Algorithm.fromString(algorithm).bind()
    runCatching { getUrl(algo, digits, step, secret, issuer, accountName) }
        .fold(
            onSuccess = { Result.Success<String, TotpException>(it) },
            onFailure = { Result.Failure(it as TotpException) }
        ).bind()
}

fun TotpServiceInterface.getInfoFromUriWithResult(
    uri: String,
): Result<TotpInfo, TotpException> = runCatching { getInfoFromUri(uri) }
    .fold(
        onSuccess = { Result.Success(it) },
        onFailure = { Result.Failure(it as TotpException) }
    )
