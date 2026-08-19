package de.davis.keygo.legacy_migration.data.repository

import at.favre.lib.crypto.bcrypt.BCrypt
import at.favre.lib.crypto.bcrypt.LongPasswordStrategies
import de.davis.keygo.legacy_migration.domain.repository.HashValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single
internal class HashValidatorImpl : HashValidator {

    override suspend fun validateHash(plainText: ByteArray, hash: ByteArray): Boolean =
        withContext(Dispatchers.Default) {
            BCrypt.verifyer(
                BCrypt.Version.VERSION_2A,
                LongPasswordStrategies.hashSha512(BCrypt.Version.VERSION_2A)
            ).verify(plainText, hash).verified
        }
}
