package de.davis.keygo.feature.password_health.domain.usecase

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.repository.LoginRepository
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.decrypt
import de.davis.keygo.core.security.domain.model.CryptoScopeError
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.asResult
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.resultBinding
import de.davis.keygo.feature.password_health.domain.LoginFingerprinter
import de.davis.keygo.feature.password_health.domain.checker.PasswordHealthChecker
import de.davis.keygo.feature.password_health.domain.model.HealthFinding
import de.davis.keygo.feature.password_health.domain.model.HealthFingerprint
import de.davis.keygo.feature.password_health.domain.model.ItemHealth
import de.davis.keygo.feature.password_health.domain.model.ItemIssue
import de.davis.keygo.feature.password_health.domain.model.PasswordCandidate
import de.davis.keygo.feature.password_health.domain.model.PasswordHealthReport
import de.davis.keygo.feature.password_health.domain.model.PasswordHealthReportError
import de.davis.keygo.feature.password_health.domain.model.RelatedGroup
import de.davis.keygo.feature.password_health.domain.model.StoredHealthItem
import de.davis.keygo.feature.password_health.domain.model.StoredHealthReport
import de.davis.keygo.feature.password_health.domain.repository.BreachCheckStateRepository
import de.davis.keygo.feature.password_health.domain.repository.HealthReportStoreRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

@Single
class PasswordHealthReportUseCase(
    private val loginRepository: LoginRepository,
    private val cryptographicScopeProvider: CryptographicScopeProvider,
    private val loginFingerprinter: LoginFingerprinter,
    private val healthReportStoreRepository: HealthReportStoreRepository,
    private val breachCheckStateRepository: BreachCheckStateRepository,
    private val checker: List<PasswordHealthChecker>,
) {

    suspend operator fun invoke(): Result<PasswordHealthReport, PasswordHealthReportError> =
        resultBinding {
            coroutineScope {
                val logins = loginRepository.observeLogins()
                    .firstOrNull()
                    ?.filter { it.passwordCredential != null }
                    .asResult(PasswordHealthReportError.NoPasswords)
                    .bind()

                val fingerprints = logins.map { login ->
                    async { loginFingerprinter.fingerprint(login)?.let { login.id to it } }
                }
                    .awaitAll()
                    .filterNotNull()
                    .toMap()

                val breachCheckEnabled = breachCheckStateRepository.getBreachCheckState().enabled
                val storedReport = healthReportStoreRepository.load().getOrNull()

                if (storedReport != null
                    && storedReport.isUpToDate(fingerprints, breachCheckEnabled)
                ) {
                    return@coroutineScope storedReport.assembleReport(logins)
                }

                val report = logins.computeReport(fingerprints, breachCheckEnabled).bind()
                return@coroutineScope report.assembleReport(logins)
            }
        }

    private fun StoredHealthReport.isUpToDate(
        fingerprints: Map<ItemId, HealthFingerprint>,
        breachCheckEnabled: Boolean
    ): Boolean {
        val fingerprintsMatch = items.associate { it.id to it.fingerprint } == fingerprints
        val expired = Clock.System.now() - generatedAt >= REPORT_TTL

        return fingerprintsMatch && !expired && this.breachCheckEnabled == breachCheckEnabled
    }

    private suspend fun Collection<Login>.computeReport(
        fingerprints: Map<ItemId, HealthFingerprint>,
        breachCheckEnabled: Boolean,
    ) = resultBinding {
        val results = coroutineScope {
            map { login -> async { login.id to candidate(login) } }.awaitAll()
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
                checker.map { async { it.type to it.check(candidates) } }.awaitAll()
            }
        } finally {
            candidates.forEach { it.password.fill('\u0000') }
        }

        val findings = outcomes.flatMap { (_, outcome) -> outcome.findings }

        val relations = findings.filterIsInstance<HealthFinding.Relation>()
        val issuesById = findings.filterIsInstance<HealthFinding.Item>()
            .groupBy(keySelector = { it.id }, valueTransform = { it.issue })

        val report = StoredHealthReport(
            items = mapToStoredHealthItems(issuesById, fingerprints, unreadable),
            relationalFindings = relations,
            gaps = emptyMap(),
            generatedAt = Clock.System.now(),
            breachCheckEnabled = breachCheckEnabled
        )

        healthReportStoreRepository.storeReport(report)
            .bind { PasswordHealthReportError.StoreFailed }
        report
    }

    private fun StoredHealthReport.assembleReport(logins: List<Login>): PasswordHealthReport {
        val loginsById = logins.associateBy { it.id }

        val issuesById = items.filterFindings()
            .filter { it.id in loginsById }
            .associate { it.id to it.issues() }

        val relations = relationalFindings.mapNotNull {
            val surviving = it.relatedItemIds.filterTo(mutableSetOf()) { it in loginsById }
            if (surviving.size > 1) it.copy(relatedItemIds = surviving) else null
        }

        val uf = UnionFind<ItemId>()
        relations.forEach { relation ->
            val first = relation.relatedItemIds.first()
            relation.relatedItemIds.forEach { uf.union(first, it) }
        }

        fun health(item: StoredHealthItem): ItemHealth {
            val login = loginsById.getValue(item.id)
            return ItemHealth(
                itemId = item.id,
                title = login.name,
                username = login.username,
                issues = issuesById[item.id].orEmpty().sortedByDescending { it.severity },
                urls = login.domainInfos.map { it.value },
            )
        }

        val (related, unrelated) = items.partition { uf.contains(it.id) }
        val relationsById = relations.groupBy { uf.find(it.relatedItemIds.first()) }

        val groups = related.groupBy { uf.find(it.id) }
            .map { (root, members) ->
                RelatedGroup(
                    id = root,
                    members = members.mapTo(mutableSetOf(), ::health),
                    relations = relationsById.getValue(root).toSet(),
                )
            }
            .sortedByDescending { it.maxSeverity }

        val standalone = unrelated.map(::health)
            .filter { it.issues.isNotEmpty() }
            .sortedByDescending { it.maxSeverity }

        val survivingGaps = gaps.mapNotNull { (kind, gap) ->
            val unchecked = gap.unchecked.filterTo(mutableSetOf()) { it in loginsById }
            if (unchecked.isNotEmpty()) kind to gap.copy(unchecked = unchecked) else null
        }.toMap()

        return PasswordHealthReport(
            groups = groups,
            standalone = standalone,
            totalPasswordsScanned = totalPasswordsScanned,
            gaps = survivingGaps,
            unreadable = items.filter { it.unreadable }
                .mapNotNullTo(mutableSetOf()) { it.id.takeIf { id -> id in loginsById } }
        )
    }

    private fun Collection<StoredHealthItem>.filterFindings() = filter {
        (it.score != null && !it.score.isNone) ||
                it.breachOccurrences > 0
    }

    private fun StoredHealthItem.issues() = buildList {
        score?.takeIf { !it.isNone }?.let { add(ItemIssue.Weak(it)) }
        if (breachOccurrences > 0) add(ItemIssue.Breached(breachOccurrences))
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
                unreadable = item.id in unreadable
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

    companion object {
        private val REPORT_TTL = 1.days
    }
}

private class UnionFind<T> {
    private val parent = HashMap<T, T>()
    fun contains(x: T) = x in parent

    fun find(x: T): T {
        val p = parent.getOrPut(x) { x }
        return if (p == x) x else find(p).also { parent[x] = it }
    }

    fun union(a: T, b: T) {
        val ra = find(a)
        val rb = find(b)
        if (ra != rb) parent[ra] = rb
    }
}
