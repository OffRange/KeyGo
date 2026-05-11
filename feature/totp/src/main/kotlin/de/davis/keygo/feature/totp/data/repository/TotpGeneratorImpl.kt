package de.davis.keygo.feature.totp.data.repository

import de.davis.keygo.core.item.domain.model.Totp
import de.davis.keygo.core.item.domain.repository.LoginRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.decrypt
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation
import de.davis.keygo.core.security.domain.crypto.wrappedItemKeyInformation
import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.totp.domain.model.TotpError
import de.davis.keygo.feature.totp.domain.model.TotpValue
import de.davis.keygo.feature.totp.domain.repository.TotpGenerator
import de.davis.keygo.rust.totp.TotpService
import de.davis.keygo.rust.totp.fromString
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
    private val loginRepository: LoginRepository,
    private val vaultRepository: VaultRepository,
) : TotpGenerator {

    override suspend fun getTotpCode(totp: Totp): Result<TotpValue, TotpError> {
        val login = loginRepository.getLoginById(totp.loginId)
            ?: return Result.Failure(TotpError.NoLoginFound)
        val wrappedVaultKey =
            vaultRepository.getKeyInformation(login.vaultId)
                ?: return Result.Failure(TotpError.NoItemKeyInfoFound)

        val decryptedSecret = cryptographicScopeProvider.itemScope(
            wrappedVaultKeyInformation = WrappedVaultKeyInformation(
                wrappedVaultKey = wrappedVaultKey,
                vaultId = login.vaultId,
            ),
            wrappedItemKeyInformation = login.wrappedItemKeyInformation()
        ) {
            totp.secret.decrypt()
        }

        val (value, _) = getTotpValue(totp, decryptedSecret)
        return Result.Success(value)
    }

    override fun observeTotpCode(totp: Totp): Flow<TotpValue> = flow {
        val login = loginRepository.getLoginById(totp.loginId) ?: return@flow
        val wrappedVaultKey = vaultRepository.getKeyInformation(login.vaultId) ?: return@flow

        val decryptedSecret = cryptographicScopeProvider.itemScope(
            wrappedVaultKeyInformation = WrappedVaultKeyInformation(
                wrappedVaultKey = wrappedVaultKey,
                vaultId = login.vaultId,
            ),
            wrappedItemKeyInformation = login.wrappedItemKeyInformation()
        ) {
            totp.secret.decrypt()
        }

        val periodMs = totp.period.seconds.inWholeMilliseconds

        while (currentCoroutineContext().isActive) {
            val (value, remaining) = getTotpValue(totp, decryptedSecret, periodMs)

            emit(value)
            delay(remaining)
        }
    }

    private fun getTotpValue(
        totp: Totp,
        decryptedSecret: String,
        periodMs: Long = totp.period.seconds.inWholeMilliseconds,
    ): Pair<TotpValue, Long> {
        val totpCode = totpService.getTotp(
            algorithm = Algorithm.fromString(totp.algorithm),
            digits = totp.digits.toUByte(),
            step = totp.period.toULong(),
            secret = decryptedSecret,
        )

        val now = System.currentTimeMillis()
        val nextWindowStart = ((now / periodMs) + 1) * periodMs
        val remaining = nextWindowStart - now

        return TotpValue(
            code = totpCode,
            validUntil = nextWindowStart,
            maxLifetime = periodMs
        ) to remaining
    }
}