package de.davis.keygo.rust.totp

import de.davis.keygo.core.util.Result
import de.davisalessandro.keygo.rust.Algorithm
import de.davisalessandro.keygo.rust.TotpException
import de.davisalessandro.keygo.rust.TotpInfo
import de.davisalessandro.keygo.rust.TotpServiceInterface
import de.davisalessandro.keygo.rust.algorithmFromString

typealias TotpService = TotpServiceInterface

fun Algorithm.Companion.fromString(value: String): Result<Algorithm, TotpException> =
    runCatching { algorithmFromString(value) }
        .fold(
            onSuccess = { Result.Success(it) },
            onFailure = { Result.Failure(it as TotpException) }
        )

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
): Result<String, TotpException> = runCatching {
    getUrl(
        algorithmFromString(algorithm),
        digits,
        step,
        secret,
        issuer,
        accountName
    )
}.fold(
    onSuccess = { Result.Success(it) },
    onFailure = { Result.Failure(it as TotpException) }
)

fun TotpServiceInterface.getInfoFromUriWithResult(
    uri: String,
): Result<TotpInfo, TotpException> = runCatching { getInfoFromUri(uri) }
    .fold(
        onSuccess = { Result.Success(it) },
        onFailure = { Result.Failure(it as TotpException) }
    )
