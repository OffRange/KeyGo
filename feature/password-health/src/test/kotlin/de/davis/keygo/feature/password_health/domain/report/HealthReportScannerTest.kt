package de.davis.keygo.feature.password_health.domain.report

import de.davis.keygo.core.item.FakeItemRepository
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.ItemKeyEnvelope
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProvider
import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.password_health.domain.checker.PasswordHealthChecker
import de.davis.keygo.feature.password_health.domain.model.CheckGap
import de.davis.keygo.feature.password_health.domain.model.CheckKind
import de.davis.keygo.feature.password_health.domain.model.CheckOutcome
import de.davis.keygo.feature.password_health.domain.model.GapReason
import de.davis.keygo.feature.password_health.domain.model.HealthFinding
import de.davis.keygo.feature.password_health.domain.model.ItemIssue
import de.davis.keygo.feature.password_health.domain.model.PasswordCandidate
import de.davis.keygo.feature.password_health.domain.model.PasswordHealthReportError
import de.davis.keygo.feature.password_health.domain.model.RelationType
import de.davis.keygo.feature.password_health.domain.model.StoredHealthReport
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class HealthReportScannerTest {

    private val itemRepository = FakeItemRepository()
    private val scopeProvider = FakeCryptographicScopeProvider(itemRepository)

    @Test
    fun checkersSeeTheDecryptedPasswords() = runTest {
        val checker = RecordingChecker(CheckKind.Strength)
        val logins = readable(0, 1)

        scan(logins, checker)

        assertEquals(
            mapOf(id(0) to "password-${id(0)}", id(1) to "password-${id(1)}"),
            checker.seen,
        )
    }

    @Test
    fun passwordsAreWipedOnceTheCheckersAreDone() = runTest {
        val checker = RecordingChecker(CheckKind.Strength)

        scan(readable(0, 1), checker)

        assertTrue(checker.candidates.all { c -> c.password.all { it == '\u0000' } })
    }

    @Test
    fun findingsLandOnTheItemsTheyAreAbout() = runTest {
        val checker = RecordingChecker(
            CheckKind.Strength,
            CheckOutcome(
                findings = listOf(
                    HealthFinding.Item(id(0), ItemIssue.Weak(PasswordScore.Weak)),
                    HealthFinding.Item(id(1), ItemIssue.Breached(4)),
                    HealthFinding.Relation(setOf(id(0), id(1)), RelationType.Reused),
                ),
            ),
        )

        val report = scan(readable(0, 1), checker).success()

        assertEquals(
            listOf(
                storedItem(id(0), score = PasswordScore.Weak),
                storedItem(id(1), breachOccurrences = 4),
            ),
            report.items,
        )
        assertEquals(
            listOf(HealthFinding.Relation(setOf(id(0), id(1)), RelationType.Reused)),
            report.relationalFindings,
        )
    }

    @Test
    fun aSkippedCheckIsKeptAsAGapUnderItsKind() = runTest {
        val gap = CheckGap(GapReason.Unreachable, setOf(id(0)))
        val breach = RecordingChecker(CheckKind.Breach, CheckOutcome(gap = gap))
        val strength = RecordingChecker(CheckKind.Strength)

        val report = scan(readable(0), breach, strength).success()

        assertEquals(mapOf<CheckKind, CheckGap>(CheckKind.Breach to gap), report.gaps)
    }

    @Test
    fun aPasswordThatWillNotDecryptIsReportedUnreadable() = runTest {
        val checker = RecordingChecker(CheckKind.Strength)
        val logins = readable(0) + login(id(1))

        val report = scan(logins, checker).success()

        assertEquals(setOf(id(0)), checker.seen.keys)
        assertEquals(listOf(false, true), report.items.map { it.unreadable })
    }

    @Test
    fun nothingToCheckIsNoPasswords() = runTest {
        val result = scan(listOf(login(id(0))), RecordingChecker(CheckKind.Strength))

        assertEquals(Result.Failure(PasswordHealthReportError.NoPasswords), result)
    }

    private suspend fun scan(logins: List<Login>, vararg checkers: PasswordHealthChecker) =
        HealthReportScanner(scopeProvider, checkers.toList()).scan(
            logins = logins,
            fingerprints = logins.associate { it.id to fingerprint(it.id) },
            breachCheckEnabled = true,
            generatedAt = Instant.fromEpochSeconds(0),
        )

    /** Logins whose item key the fake provider can find, so their passwords decrypt. */
    private fun readable(vararg ns: Int) = ns.map { n ->
        itemRepository.seedEnvelope(
            ItemKeyEnvelope(
                vaultId = VAULT_ID,
                itemId = id(n),
                itemKeyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
                vaultKeyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
            ),
        )
        login(id(n))
    }

    private fun <S, E> Result<S, E>.success(): S = assertIs<Result.Success<S, E>>(this).success

    private class RecordingChecker(
        override val type: CheckKind,
        private val outcome: CheckOutcome = CheckOutcome(),
    ) : PasswordHealthChecker {

        var candidates: List<PasswordCandidate> = emptyList()
        var seen: Map<ItemId, String> = emptyMap()

        override suspend fun check(candidates: List<PasswordCandidate>): CheckOutcome {
            this.candidates = candidates
            seen = candidates.associate { it.id to it.password.concatToString() }
            return outcome
        }
    }
}
