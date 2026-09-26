package de.davis.keygo.feature.password_health.domain.checker

import android.util.Log
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.password_health.domain.model.BreachedError
import de.davis.keygo.feature.password_health.domain.model.CheckGap
import de.davis.keygo.feature.password_health.domain.model.CheckKind
import de.davis.keygo.feature.password_health.domain.model.CheckOutcome
import de.davis.keygo.feature.password_health.domain.model.GapReason
import de.davis.keygo.feature.password_health.domain.model.HealthFinding
import de.davis.keygo.feature.password_health.domain.model.ItemIssue
import de.davis.keygo.feature.password_health.domain.model.PasswordCandidate
import de.davis.keygo.feature.password_health.domain.repository.BreachedRepository
import de.davis.keygo.feature.password_health.domain.repository.BreachedRepository.Companion.PREFIX_LENGTH
import de.davis.keygo.feature.password_health.domain.repository.ConnectivityRepository
import de.davis.keygo.feature.password_health.domain.repository.HealthSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.nio.CharBuffer
import java.security.MessageDigest

@Single
internal class BreachedPasswordCheck(
    private val breachedRepository: BreachedRepository,
    private val healthSettingsRepository: HealthSettingsRepository,
    private val connectivityRepository: ConnectivityRepository,
) : PasswordHealthChecker {

    override val type = CheckKind.Breach

    override suspend fun check(candidates: List<PasswordCandidate>): CheckOutcome {
        if (candidates.isEmpty()) return CheckOutcome()

        if (!healthSettingsRepository.getBreachCheckState().breachesEnabled)
            return CheckOutcome.skipped(GapReason.Disabled, candidates)

        if (!connectivityRepository.hasInternet())
            return CheckOutcome.skipped(GapReason.Unreachable, candidates)

        val ranges = candidates.byRange()

        val answers = coroutineScope {
            ranges.map { (prefix, bySuffix) ->
                async { bySuffix to breachedRepository.occurrences(prefix, bySuffix.keys) }
            }.awaitAll()
        }

        val findings = mutableListOf<HealthFinding>()
        val unchecked = mutableSetOf<ItemId>()
        val reasons = mutableSetOf<GapReason>()

        answers.forEach { (bySuffix, answer) ->
            when (answer) {
                is Result.Success -> findings += answer.success.findings(bySuffix)

                is Result.Failure -> {
                    bySuffix.values.forEach { sharing -> sharing.mapTo(unchecked) { it.id } }
                    reasons += answer.error.asGapReason()
                }
            }
        }

        val gap = when {
            unchecked.isEmpty() -> null

            // A connection that is down explains the run better than one range answering oddly.
            GapReason.Unreachable in reasons ->
                CheckGap(reason = GapReason.Unreachable, unchecked = unchecked)

            else -> CheckGap(reason = GapReason.Failed, unchecked = unchecked)
        }

        if (gap != null) Log.w(
            TAG,
            "${unchecked.size} of ${candidates.size} passwords went unchecked: ${gap.reason}",
        )

        return CheckOutcome(findings = findings, gap = gap)
    }

    private fun Map<String, Int>.findings(
        bySuffix: Map<String, List<PasswordCandidate>>,
    ): List<HealthFinding> = flatMap { (suffix, occurrences) ->
        bySuffix[suffix].orEmpty().map {
            HealthFinding.Item(
                id = it.id,
                issue = ItemIssue.Breached(occurrences = occurrences),
            )
        }
    }

    private fun BreachedError.asGapReason() = when (this) {
        BreachedError.Unreachable -> GapReason.Unreachable
        BreachedError.ApiFailed, BreachedError.InvalidPrefix -> GapReason.Failed
    }

    private suspend fun List<PasswordCandidate>.byRange(): Map<String, Map<String, List<PasswordCandidate>>> {
        val sha1 = MessageDigest.getInstance("SHA-1")
        val ranges = mutableMapOf<String, MutableMap<String, MutableList<PasswordCandidate>>>()

        forEach { candidate ->
            val hash = sha1.hash(candidate.password)
            ranges.getOrPut(hash.take(PREFIX_LENGTH)) { mutableMapOf() }
                .getOrPut(hash.substring(PREFIX_LENGTH)) { mutableListOf() }
                .add(candidate)
        }

        return ranges
    }

    private suspend fun MessageDigest.hash(password: CharArray): String =
        withContext(Dispatchers.Default) {
            val encoded = Charsets.UTF_8.encode(CharBuffer.wrap(password))
            try {
                update(encoded)
                return@withContext digest().toHexString(format = HexFormat.UpperCase)
            } finally {
                if (encoded.hasArray()) encoded.array().fill(0)
            }
        }

    companion object {
        private const val TAG = "BreachedPasswordCheck"
    }
}
