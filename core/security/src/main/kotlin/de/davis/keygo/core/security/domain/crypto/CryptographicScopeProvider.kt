package de.davis.keygo.core.security.domain.crypto

interface CryptographicScopeProvider {
    suspend fun <R> scope(block: suspend CryptographicScope.() -> R): R
}