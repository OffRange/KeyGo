package de.davis.keygo.feature.list_screen.domain.repository

import de.davis.keygo.feature.list_screen.domain.model.SelectedVault
import kotlinx.coroutines.flow.Flow

interface SelectedVaultRepository {
    fun observe(): Flow<SelectedVault>
    suspend fun set(selection: SelectedVault)
}
