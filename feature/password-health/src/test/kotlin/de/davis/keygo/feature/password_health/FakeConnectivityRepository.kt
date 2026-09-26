package de.davis.keygo.feature.password_health

import de.davis.keygo.feature.password_health.domain.repository.ConnectivityRepository

internal class FakeConnectivityRepository(var online: Boolean = true) : ConnectivityRepository {

    override fun hasInternet(): Boolean = online
}
