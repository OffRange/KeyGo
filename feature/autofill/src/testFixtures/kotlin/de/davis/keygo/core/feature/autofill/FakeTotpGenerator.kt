package de.davis.keygo.core.feature.autofill

import de.davis.keygo.core.item.domain.model.Totp
import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.totp.domain.model.TotpError
import de.davis.keygo.feature.totp.domain.model.TotpValue
import de.davis.keygo.feature.totp.domain.repository.TotpGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Settable [TotpGenerator] for tests.
 * Set [result] before calling [getTotpCode].
 */
class FakeTotpGenerator : TotpGenerator {
    var result: Result<TotpValue, TotpError> = Result.Failure(TotpError.CryptoFailed)
    override suspend fun getTotpCode(totp: Totp): Result<TotpValue, TotpError> = result
    override fun observeTotpCode(totp: Totp): Flow<Result<TotpValue, TotpError>> = flowOf(result)
}
