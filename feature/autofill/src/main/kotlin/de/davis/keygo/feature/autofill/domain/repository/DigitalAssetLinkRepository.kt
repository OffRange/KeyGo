package de.davis.keygo.feature.autofill.domain.repository

interface DigitalAssetLinkRepository {

    suspend fun isLinked(packageName: String, signature: String, domain: String): Boolean
}