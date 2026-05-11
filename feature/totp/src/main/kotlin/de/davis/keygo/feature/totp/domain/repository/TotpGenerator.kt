package de.davis.keygo.feature.totp.domain.repository

import de.davis.keygo.core.item.domain.model.Totp
import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.totp.domain.model.TotpError
import de.davis.keygo.feature.totp.domain.model.TotpValue
import kotlinx.coroutines.flow.Flow

interface TotpGenerator {

    suspend fun getTotpCode(totp: Totp): Result<TotpValue, TotpError>

    fun observeTotpCode(totp: Totp): Flow<TotpValue>
}

