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
import de.davis.keygo.feature.password_health.domain.model.BreachResult
import de.davis.keygo.feature.password_health.domain.model.CheckKind
import de.davis.keygo.feature.password_health.domain.model.CheckOutcome
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
        previous: StoredHealthReport?,
        breachCheckEnabled: Boolean,
        now: Instant,
    ): Result<StoredHealthReport, PasswordHealthReportError> = resultBinding {
        val keptBreaches = previous
            ?.takeIf { breachCheckEnabled }
            ?.breachResultsAt(fingerprints, now)
            .orEmpty()

        // Strength, reuse and similarity only move with the passwords, so a report that still
        // matches them is kept and only the breach lookups that ran out are repeated.
        val current = previous?.takeIf { it.isCurrentFor(fingerprints, breachCheckEnabled) }

        if (current != null) refreshBreaches(current, logins, keptBreaches, now)
        else fullScan(logins, fingerprints, keptBreaches, breachCheckEnabled, now).bind()
    }

    private suspend fun fullScan(
        logins: Collection<Login>,
        fingerprints: Map<ItemId, HealthFingerprint>,
        keptBreaches: Map<ItemId, BreachResult>,
        breachCheckEnabled: Boolean,
        now: Instant,
    ): Result<StoredHealthReport, PasswordHealthReportError> = resultBinding {
        val decrypted = decrypt(logins)
        decrypted.candidates.isNotEmpty().asResult(
            if (decrypted.unreadable.isEmpty()) PasswordHealthReportError.NoPasswords
            else PasswordHealthReportError.Unreadable,
        ).bind()

        val outcomes = check(checkers, decrypted.candidates, keptBreaches)
        val findings = outcomes.values.flatMap { it.findings }
        val weak = findings.issues<ItemIssue.Weak>()
        val breaches = outcomes.breachResults(decrypted.candidates, keptBreaches, now)

        StoredHealthReport(
            items = logins.mapNotNull { login ->
                fingerprints[login.id]?.let { fingerprint ->
                    StoredHealthItem(
                        id = login.id,
                        fingerprint = fingerprint,
                        score = weak[login.id]?.score,
                        breach = breaches[login.id],
                        unreadable = login.id in decrypted.unreadable,
                    )
                }
            },
            relationalFindings = findings.filterIsInstance<HealthFinding.Relation>(),
            gaps = outcomes.gaps(),
            breachCheckEnabled = breachCheckEnabled,
        )
    }

    private suspend fun refreshBreaches(
        current: StoredHealthReport,
        logins: Collection<Login>,
        keptBreaches: Map<ItemId, BreachResult>,
        now: Instant,
    ): StoredHealthReport {
        val decrypted = decrypt(logins.filter { it.id !in keptBreaches })
        val breachCheckers = checkers.filter { it.type == CheckKind.Breach }

        val outcomes = check(breachCheckers, decrypted.candidates, keptBreaches)
        val breaches = outcomes.breachResults(decrypted.candidates, keptBreaches, now)

        return current.copy(
            items = current.items.map {
                it.copy(breach = breaches[it.id], unreadable = it.id in decrypted.unreadable)
            },
            gaps = current.gaps - CheckKind.Breach + outcomes.gaps(),
        )
    }

    private suspend fun decrypt(logins: Collection<Login>): Decrypted {
        val results = coroutineScope {
            logins.map { login -> async { login.id to candidate(login) } }.awaitAll()
        }

        // A password that will not decrypt is checked by nobody, so it is reported instead
        // of quietly dropping out of the count.
        return Decrypted(
            candidates = results.mapNotNull { (_, result) -> result?.getOrNull() },
            unreadable = results
                .filter { (_, result) -> result?.isFailure() == true }
                .mapTo(mutableSetOf()) { (id, _) -> id },
        )
    }

    private suspend fun check(
        checkers: List<PasswordHealthChecker>,
        candidates: List<PasswordCandidate>,
        keptBreaches: Map<ItemId, BreachResult>,
    ): Map<CheckKind, CheckOutcome> = try {
        coroutineScope {
            checkers.map { checker ->
                async {
                    // Without the filter, the breach checker would look up every password again
                    // after any edit, and incremental breach lookups would no longer happen.
                    val input = if (checker.type == CheckKind.Breach)
                        candidates.filter { it.id !in keptBreaches }
                    else candidates

                    checker.type to checker.check(input)
                }
            }.awaitAll().toMap()
        }
    } finally {
        candidates.forEach { it.password.fill('\u0000') }
    }

    private fun Map<CheckKind, CheckOutcome>.breachResults(
        candidates: List<PasswordCandidate>,
        keptBreaches: Map<ItemId, BreachResult>,
        now: Instant,
    ): Map<ItemId, BreachResult> {
        val outcome = this[CheckKind.Breach] ?: return keptBreaches
        val breached = outcome.findings.issues<ItemIssue.Breached>()
        val unchecked = outcome.gap?.unchecked.orEmpty()

        return keptBreaches + candidates.map { it.id }
            .filter { it !in keptBreaches && it !in unchecked }
            .associateWith {
                BreachResult(
                    occurrences = breached[it]?.occurrences ?: 0,
                    checkedAt = now
                )
            }
    }

    private fun Map<CheckKind, CheckOutcome>.gaps() =
        mapNotNull { (kind, outcome) -> outcome.gap?.let { kind to it } }.toMap()

    private inline fun <reified T : ItemIssue> List<HealthFinding>.issues(): Map<ItemId, T> =
        filterIsInstance<HealthFinding.Item>()
            .mapNotNull { finding -> (finding.issue as? T)?.let { finding.id to it } }
            .toMap()

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

    private class Decrypted(
        val candidates: List<PasswordCandidate>,
        val unreadable: Set<ItemId>,
    )
}
