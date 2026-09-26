package de.davis.keygo.feature.password_health.domain.repository

interface ConnectivityRepository {
    fun hasInternet(): Boolean
}
