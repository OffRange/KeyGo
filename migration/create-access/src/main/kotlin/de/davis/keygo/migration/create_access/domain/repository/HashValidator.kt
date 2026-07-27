package de.davis.keygo.migration.create_access.domain.repository

internal interface HashValidator {

    suspend fun validateHash(
        plainText: ByteArray,
        hash: ByteArray
    ): Boolean
}