package de.davis.keygo.feature.password_health.domain.repository

import de.davis.keygo.feature.password_health.domain.model.BreachCheckState
import kotlinx.coroutines.flow.Flow

interface BreachCheckStateRepository {
    fun observeBreachCheckState(): Flow<BreachCheckState>
    suspend fun getBreachCheckState(): BreachCheckState
    suspend fun setBreachEnabled(enabled: Boolean)
}
