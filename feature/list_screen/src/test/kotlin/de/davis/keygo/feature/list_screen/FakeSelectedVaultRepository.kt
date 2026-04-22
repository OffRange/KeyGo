package de.davis.keygo.feature.list_screen

import de.davis.keygo.feature.list_screen.domain.model.SelectedVault
import de.davis.keygo.feature.list_screen.domain.repository.SelectedVaultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [SelectedVaultRepository] for tests. Mirrors the observe/set contract
 * of the real DataStore-backed impl without pulling in file I/O.
 */
internal class FakeSelectedVaultRepository(
    initial: SelectedVault = SelectedVault.All,
) : SelectedVaultRepository {

    private val flow = MutableStateFlow(initial)

    override fun observe(): Flow<SelectedVault> = flow

    override suspend fun set(selection: SelectedVault) {
        flow.value = selection
    }

    val current: SelectedVault get() = flow.value
}
