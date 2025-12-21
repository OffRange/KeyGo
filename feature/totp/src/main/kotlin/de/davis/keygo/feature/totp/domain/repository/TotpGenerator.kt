package de.davis.keygo.feature.totp.domain.repository

import de.davis.keygo.feature.totp.domain.model.TotpInformation
import kotlinx.coroutines.flow.Flow

interface TotpGenerator {

    fun observeTotp(encodedSecret: ByteArray): Flow<TotpInformation>
}

