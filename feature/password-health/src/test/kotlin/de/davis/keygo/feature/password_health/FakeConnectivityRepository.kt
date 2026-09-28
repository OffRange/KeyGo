package de.davis.keygo.feature.password_health

import de.davis.keygo.feature.password_health.domain.repository.ConnectivityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeConnectivityRepository(online: Boolean = true) : ConnectivityRepository {

    private val internet = MutableStateFlow(online)

    var online: Boolean
        get() = internet.value
        set(value) {
            internet.value = value
        }

    override fun observeInternet(): Flow<Boolean> = internet
}
