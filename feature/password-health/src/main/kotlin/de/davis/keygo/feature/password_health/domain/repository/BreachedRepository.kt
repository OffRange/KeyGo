package de.davis.keygo.feature.password_health.domain.repository

import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.password_health.domain.model.BreachedError

interface BreachedRepository {

    suspend fun occurrences(
        prefix: String,
        suffixes: Set<String>,
    ): Result<Map<String, Int>, BreachedError>

    companion object {
        const val PREFIX_LENGTH = 5
    }
}
