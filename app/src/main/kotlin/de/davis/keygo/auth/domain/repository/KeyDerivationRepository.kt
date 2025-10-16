package de.davis.keygo.auth.domain.repository

import de.davis.keygo.core.identity.common.domain.model.CryptographyError
import de.davis.keygo.core.util.Result

interface KeyDerivationRepository {

    suspend fun deriveKey(
        password: ByteArray,
        salt: ByteArray,
        timeCost: Int = 3,
        memoryCostInKiB: Int = 131_072, /* 128 MiB */
        parallelism: Int = 4,
        outputLengthInBytes: Int = 32, /* 256 bits */
    ): Result<ByteArray, CryptographyError>
}