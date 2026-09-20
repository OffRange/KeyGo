package de.davis.keygo.feature.password_health.domain.repository

import de.davis.keygo.feature.password_health.domain.model.BreachCheckState

interface BreachCheckStateRepository {
    suspend fun getBreachCheckState(): BreachCheckState
    suspend fun setBreachEnabled(enabled: Boolean)
}
