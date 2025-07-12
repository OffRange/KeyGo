package de.davis.keygo.core.domain.model

import de.davis.keygo.core.domain.model.crypto.CryptographicData

@JvmInline
value class TotpSecret(val encodedSecret: CryptographicData)

fun CryptographicData.asTotpSecret(): TotpSecret = TotpSecret(this)
