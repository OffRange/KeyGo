package de.davis.keygo.legacy_migration.domain.repository

internal interface HashValidator {

    suspend fun validateHash(
        plainText: ByteArray,
        hash: ByteArray
    ): Boolean
}
