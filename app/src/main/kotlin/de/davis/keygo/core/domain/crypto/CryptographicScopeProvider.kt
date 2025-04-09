package de.davis.keygo.core.domain.crypto

interface CryptographicScopeProvider {
    suspend fun <R> scope(block: suspend CryptographicScope.() -> R): R
}