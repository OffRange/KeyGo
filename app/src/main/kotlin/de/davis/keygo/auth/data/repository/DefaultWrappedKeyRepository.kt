package de.davis.keygo.auth.data.repository

import androidx.datastore.core.DataStore
import de.davis.keygo.auth.domain.repository.WrappedKeyRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

open class DefaultWrappedKeyRepository<Proto, Domain>(
    private val dataStore: DataStore<Proto>,
    private val toDomain: Proto.() -> Domain,
    private val toProto: Domain.() -> Proto,
    private val defaultInstance: () -> Proto
) : WrappedKeyRepository<Domain> {

    override suspend fun getWrappedKeyData(): Domain? =
        dataStore.data.map { it.toDomain() }.firstOrNull()

    override suspend fun setWrappedKeyData(keyData: Domain) {
        dataStore.updateData { keyData.toProto() }
    }

    override suspend fun clearWrappedKeyData() {
        dataStore.updateData { defaultInstance() }
    }
}