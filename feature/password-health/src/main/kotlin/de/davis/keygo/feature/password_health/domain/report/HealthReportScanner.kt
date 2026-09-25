package de.davis.keygo.feature.password_health.domain.report

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.decrypt
import de.davis.keygo.core.security.domain.model.CryptoScopeError
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.asResult
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.resultBinding
import de.davis.keygo.feature.password_health.domain.checker.PasswordHealthChecker
import de.davis.keygo.feature.password_health.domain.model.HealthFinding
import de.davis.keygo.feature.password_health.domain.model.HealthFingerprint
import de.davis.keygo.feature.password_health.domain.model.ItemIssue
import de.davis.keygo.feature.password_health.domain.model.PasswordCandidate
import de.davis.keygo.feature.password_health.domain.model.PasswordHealthReportError
import de.davis.keygo.feature.password_health.domain.model.StoredHealthItem
import de.davis.keygo.feature.password_health.domain.model.StoredHealthReport
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.koin.core.annotation.Single
import kotlin.time.Instant

@Single
class HealthReportScanner(
    private val cryptographicScopeProvider: CryptographicScopeProvider,
    private val checkers: List<PasswordHealthChecker>,
) {

    suspend fun scan(
        logins: Collection<Login>,
        fingerprints: Map<ItemId, HealthFingerprint>,
        breachCheckEnabled: Boolean,
        generatedAt: Instant,
    ): Result<StoredHealthReport, PasswordHealthReportError> = resultBinding {
        val results = coroutineScope {
            logins.map { login -> async { login.id to candidate(login) } }.awaitAll()
        }

        // A password that will not decrypt is checked by nobody, so it is reported instead
        // of quietly dropping out of the count.
        val unreadable = results
            .filter { (_, result) -> result?.isFailure() == true }
            .mapTo(mutableSetOf()) { (id, _) -> id }

        val candidates = results.mapNotNull { (_, result) -> result?.getOrNull() }
        candidates.isNotEmpty().asResult(PasswordHealthReportError.NoPasswords).bind()

        val outcomes = try {
            coroutineScope {
                checkers.map { async { it.type to it.check(candidates) } }.awaitAll()
            }
        } finally {
            candidates.forEach { it.password.fill('\u0000') }
        }

        val findings = outcomes.flatMap { (_, outcome) -> outcome.findings }
        val gaps = outcomes.mapNotNull { (type, outcome) -> outcome.gap?.let { type to it } }
            .toMap()

        val relations = findings.filterIsInstance<HealthFinding.Relation>()
        val issuesById = findings.filterIsInstance<HealthFinding.Item>()
            .groupBy(keySelector = { it.id }, valueTransform = { it.issue })

        StoredHealthReport(
            items = logins.mapToStoredHealthItems(issuesById, fingerprints, unreadable),
            relationalFindings = relations,
            gaps = gaps,
            generatedAt = generatedAt,
            breachCheckEnabled = breachCheckEnabled,
        )
    }

    private fun Collection<Login>.mapToStoredHealthItems(
        issuesById: Map<ItemId, List<ItemIssue>>,
        fingerprints: Map<ItemId, HealthFingerprint>,
        unreadable: Set<ItemId>,
    ): List<StoredHealthItem> = mapNotNull { item ->
        val issues = issuesById[item.id].orEmpty()
        val score = issues.filterIsInstance<ItemIssue.Weak>().firstOrNull()?.score
        val breachOccurrences = issues.filterIsInstance<ItemIssue.Breached>().firstOrNull()
            ?.occurrences
            ?: 0

        fingerprints[item.id]?.let { fingerprint ->
            StoredHealthItem(
                id = item.id,
                fingerprint = fingerprint,
                score = score,
                breachOccurrences = breachOccurrences,
                unreadable = item.id in unreadable,
            )
        }
    }

    private suspend fun candidate(login: Login): Result<PasswordCandidate, CryptoScopeError>? =
        login.passwordCredential?.let { credential ->
            cryptographicScopeProvider.itemScope(
                itemId = login.id,
            ) {
                PasswordCandidate(
                    id = login.id,
                    score = credential.score,
                    password = credential.secret.decrypt().toCharArray(),
                )
            }
        }
}
