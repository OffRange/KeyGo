package de.davis.keygo.auth.domain.repository

interface WrappedKeyRepository<T> {

    suspend fun getWrappedKeyData(): T?
    suspend fun setWrappedKeyData(keyData: T)
    suspend fun clearWrappedKeyData()
}