package de.davis.keygo.totp.domain.repository

import de.davis.keygo.totp.domain.model.TotpInformation
import kotlinx.coroutines.flow.Flow

interface TotpGenerator {

    fun observeTotp(encodedSecret: ByteArray): Flow<TotpInformation>
}

