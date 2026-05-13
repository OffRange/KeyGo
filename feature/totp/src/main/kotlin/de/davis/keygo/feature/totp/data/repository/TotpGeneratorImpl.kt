package de.davis.keygo.feature.totp.data.repository

import de.davis.keygo.core.item.domain.model.Totp
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.decrypt
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.mapFailure
import de.davis.keygo.core.util.mapSuccess
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.core.util.resultBinding
import de.davis.keygo.feature.totp.domain.model.TotpError
import de.davis.keygo.feature.totp.domain.model.TotpValue
import de.davis.keygo.feature.totp.domain.repository.TotpGenerator
import de.davis.keygo.rust.totp.TotpService
import de.davis.keygo.rust.totp.fromString
import de.davis.keygo.rust.totp.getTotpWithResult
import de.davisalessandro.keygo.rust.Algorithm
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import org.koin.core.annotation.Single
import kotlin.time.Duration.Companion.seconds

@Single
internal class TotpGeneratorImpl(
    private val totpService: TotpService,
    private val cryptographicScopeProvider: CryptographicScopeProvider,
) : TotpGenerator {

    override suspend fun getTotpCode(totp: Totp): Result<TotpValue, TotpError> = resultBinding {
        val decryptedSecret = totp.decryptSecret().bind()

        val (value, _) = getTotpValue(totp, decryptedSecret).bind()
        value
    }

    override fun observeTotpCode(totp: Totp): Flow<Result<TotpValue, TotpError>> = flow {
        totp.decryptSecret().mapSuccess { decryptedSecret ->
            val periodMs = totp.period.seconds.inWholeMilliseconds

            while (currentCoroutineContext().isActive) {
                getTotpValue(totp, decryptedSecret, periodMs).onSuccess { (value, remaining) ->
                    emit(Result.Success(value))
                    delay(remaining)
                }.onFailure {
                    emit(Result.Failure(it))
                    return@flow
                }
            }
        }
    }

    private fun getTotpValue(
        totp: Totp,
        decryptedSecret: String,
        periodMs: Long = totp.period.seconds.inWholeMilliseconds,
    ): Result<Pair<TotpValue, Long>, TotpError> = resultBinding {
        val totpCode = totpService.getTotpWithResult(
            algorithm = Algorithm.fromString(totp.algorithm)
                .bind { TotpError.UnsupportedAlgorithm },
            digits = totp.digits,
            step = totp.period,
            secret = decryptedSecret,
        ).bind(TotpError::RustFailed)

        val periodMs = periodMs.coerceAtLeast(1L)
        val now = System.currentTimeMillis()
        val nextWindowStart = ((now / periodMs) + 1) * periodMs
        val remaining = nextWindowStart - now

        TotpValue(
            code = totpCode,
            validUntil = nextWindowStart,
            maxLifetime = periodMs
        ) to remaining
    }

    private suspend fun Totp.decryptSecret(): Result<String, TotpError> {
        return cryptographicScopeProvider.itemScope(itemId = loginId) {
            secret.decrypt()
        }.mapFailure { TotpError.CryptoFailed }
    }
}