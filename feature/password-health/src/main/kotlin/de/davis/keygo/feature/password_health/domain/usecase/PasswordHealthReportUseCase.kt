package de.davis.keygo.feature.password_health.domain.usecase

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.repository.LoginRepository
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.decrypt
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.asResult
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.resultBinding
import de.davis.keygo.feature.password_health.domain.checker.PasswordHealthChecker
import de.davis.keygo.feature.password_health.domain.model.HealthFinding
import de.davis.keygo.feature.password_health.domain.model.ItemHealth
import de.davis.keygo.feature.password_health.domain.model.PasswordCandidate
import de.davis.keygo.feature.password_health.domain.model.PasswordHealthReport
import de.davis.keygo.feature.password_health.domain.model.PasswordHealthReportError
import de.davis.keygo.feature.password_health.domain.model.RelatedGroup
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.annotation.Single

@Single
class PasswordHealthReportUseCase(
    private val loginRepository: LoginRepository,
    private val cryptographicScopeProvider: CryptographicScopeProvider,
    private val checker: List<PasswordHealthChecker>,
) {

    suspend operator fun invoke(): Result<PasswordHealthReport, PasswordHealthReportError> =
        resultBinding {
            val candidates = loginRepository.observeLogins()
                .firstOrNull()
                ?.mapNotNull {
                    it.passwordCredential?.let { credential ->
                        cryptographicScopeProvider.itemScope(
                            itemId = it.id,
                        ) {
                            PasswordCandidate(
                                id = it.id,
                                title = it.name,
                                username = it.username,
                                score = credential.score,
                                password = credential.secret.decrypt().toCharArray(),
                            )
                        }.getOrNull() // TODO
                    }
                }
                .asResult(PasswordHealthReportError.NoPasswords)
                .bind()

            val findings = try {
                coroutineScope {
                    checker.map { async { it.check(candidates) } }.awaitAll().flatten()
                }
            } finally {
                candidates.forEach { it.password.fill('\u0000') }
            }

            val issuesById = findings.filterIsInstance<HealthFinding.Item>()
                .groupBy(keySelector = { it.id }, valueTransform = { it.issue })
            val relations = findings.filterIsInstance<HealthFinding.Relation>()

            // Connected components over all relation edges
            val uf = UnionFind<ItemId>()
            relations.forEach { r ->
                val first = r.relatedItemIds.first()
                r.relatedItemIds.forEach { uf.union(first, it) }
            }

            fun health(item: PasswordCandidate) = ItemHealth(
                itemId = item.id,
                title = item.title,
                username = item.username,
                issues = issuesById[item.id].orEmpty().sortedByDescending { it.severity },
            )

            val (related, unrelated) = candidates.partition { uf.contains(it.id) }
            val relationsById = relations.groupBy { uf.find(it.relatedItemIds.first()) }

            val group = related.groupBy { uf.find(it.id) }
                .map { (root, members) ->
                    RelatedGroup(
                        id = root,
                        members = members.mapTo(
                            mutableSetOf(),
                            ::health
                        ),
                        relations = relationsById.getValue(root).toSet()
                    )
                }
                .sortedByDescending { it.maxSeverity }

            val standalone = unrelated.map(::health)
                .filter { it.issues.isNotEmpty() }
                .sortedByDescending { it.maxSeverity }

            PasswordHealthReport(
                groups = group,
                standalone = standalone,
                totalPasswordsScanned = candidates.size
            )
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
