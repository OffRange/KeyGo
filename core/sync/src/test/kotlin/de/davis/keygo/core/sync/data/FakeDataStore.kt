package de.davis.keygo.core.sync.data

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeDataStore<T>(initial: T) : DataStore<T> {

    val state = MutableStateFlow(initial)

    override val data: Flow<T> = state

    override suspend fun updateData(transform: suspend (t: T) -> T): T =
        transform(state.value).also { state.value = it }
}
