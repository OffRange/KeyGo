package de.davis.keygo.feature.password_health.domain.repository

import kotlinx.coroutines.flow.Flow

interface ConnectivityRepository {
    fun observeInternet(): Flow<Boolean>
}
