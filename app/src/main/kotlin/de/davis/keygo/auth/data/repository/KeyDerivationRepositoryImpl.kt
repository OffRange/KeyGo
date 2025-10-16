package de.davis.keygo.auth.data.repository

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import com.lambdapioneer.argon2kt.Argon2Version
import de.davis.keygo.auth.domain.repository.KeyDerivationRepository
import de.davis.keygo.core.identity.common.domain.model.CryptographyError
import de.davis.keygo.core.util.Result
import org.koin.core.annotation.Single

@Single
class KeyDerivationRepositoryImpl(
    private val argon2Kt: Argon2Kt,
) : KeyDerivationRepository {

    override suspend fun deriveKey(
        password: ByteArray,
        salt: ByteArray,
        timeCost: Int,
        memoryCostInKiB: Int,
        parallelism: Int,
        outputLengthInBytes: Int
    ): Result<ByteArray, CryptographyError> = runCatching {
        argon2Kt.hash(
            mode = Argon2Mode.ARGON2_ID,
            password = password,
            salt = salt,
            tCostInIterations = timeCost,
            mCostInKibibyte = memoryCostInKiB,
            parallelism = parallelism,
            hashLengthInBytes = outputLengthInBytes,
            version = Argon2Version.V13
        )
    }.fold(
        onSuccess = { r -> Result.Success(r.rawHashAsByteArray()) },
        onFailure = { Result.Failure(CryptographyError.DerivationFailed(it.message ?: "")) }
    )
}