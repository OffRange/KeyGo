package de.davis.keygo.core.security.domain.crypto.model

import de.davis.keygo.core.item.domain.model.KeyInformation

class SealedAccountRecord(
    val wrappedKey: KeyInformation,
    val data: CryptographicData,
)
