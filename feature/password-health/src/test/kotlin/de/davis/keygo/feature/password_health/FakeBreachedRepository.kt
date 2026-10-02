package de.davis.keygo.feature.password_health

import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.password_health.domain.model.BreachedError
import de.davis.keygo.feature.password_health.domain.repository.BreachedRepository
import de.davis.keygo.feature.password_health.domain.repository.BreachedRepository.Companion.PREFIX_LENGTH
import java.util.Collections

/**
 * Answers range lookups from [breaches], keyed by the full upper-case SHA-1 hex of a password.
 * A prefix in [failures] fails with its error instead.
 */
internal class FakeBreachedRepository(
    var breaches: Map<String, Int> = emptyMap(),
    val failures: MutableMap<String, BreachedError> = mutableMapOf(),
) : BreachedRepository {

    data class Call(val prefix: String, val suffixes: Set<String>)

    val calls: MutableList<Call> = Collections.synchronizedList(mutableListOf())

    override suspend fun occurrences(
        prefix: String,
        suffixes: Set<String>,
    ): Result<Map<String, Int>, BreachedError> {
        calls += Call(prefix, suffixes)
        failures[prefix]?.let { return Result.Failure(it) }

        return Result.Success(
            breaches
                .filterKeys { it.startsWith(prefix) && it.substring(PREFIX_LENGTH) in suffixes }
                .mapKeys { (hash, _) -> hash.substring(PREFIX_LENGTH) },
        )
    }
}
