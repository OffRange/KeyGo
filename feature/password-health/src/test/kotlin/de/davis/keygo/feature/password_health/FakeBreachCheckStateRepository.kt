package de.davis.keygo.feature.password_health

import de.davis.keygo.feature.password_health.domain.model.BreachCheckState
import de.davis.keygo.feature.password_health.domain.repository.BreachCheckStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeBreachCheckStateRepository(enabled: Boolean = true) : BreachCheckStateRepository {

    val state = MutableStateFlow(BreachCheckState(enabled = enabled))

    override fun observeBreachCheckState(): Flow<BreachCheckState> = state

    override suspend fun getBreachCheckState(): BreachCheckState = state.value

    override suspend fun setBreachEnabled(enabled: Boolean) {
        state.value = BreachCheckState(enabled = enabled)
    }
}
