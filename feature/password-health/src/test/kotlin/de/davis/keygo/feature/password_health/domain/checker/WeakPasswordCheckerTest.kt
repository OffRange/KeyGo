package de.davis.keygo.feature.password_health.domain.checker

import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.feature.password_health.domain.model.CheckKind
import de.davis.keygo.feature.password_health.domain.model.HealthFinding
import de.davis.keygo.feature.password_health.domain.model.ItemIssue
import de.davis.keygo.feature.password_health.domain.model.PasswordCandidate
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeakPasswordCheckerTest {

    private val checker = WeakPasswordChecker()

    @Test
    fun reportsAsTheStrengthCheck() {
        assertEquals(CheckKind.Strength, checker.type)
    }

    @Test
    fun flagsRidiculousAndWeakScores() = runTest {
        val outcome = checker.check(
            listOf(
                candidate(0, PasswordScore.Ridiculous),
                candidate(1, PasswordScore.Weak),
            ),
        )

        assertEquals(
            listOf(
                HealthFinding.Item(id(0), ItemIssue.Weak(PasswordScore.Ridiculous)),
                HealthFinding.Item(id(1), ItemIssue.Weak(PasswordScore.Weak)),
            ),
            outcome.findings,
        )
        assertNull(outcome.gap)
    }

    @Test
    fun leavesModerateAndStrongerScoresAlone() = runTest {
        val outcome = checker.check(
            listOf(
                candidate(0, PasswordScore.Moderate),
                candidate(1, PasswordScore.Strong),
                candidate(2, PasswordScore.Excellent),
            ),
        )

        assertTrue(outcome.findings.isEmpty())
    }

    @Test
    fun anUnscoredPasswordIsNotCalledWeak() = runTest {
        val outcome = checker.check(listOf(candidate(0, PasswordScore.None)))

        assertTrue(outcome.findings.isEmpty())
    }

    @Test
    fun onlyTheWeakOnesOfAMixedVaultAreFlagged() = runTest {
        val outcome = checker.check(
            listOf(
                candidate(0, PasswordScore.Strong),
                candidate(1, PasswordScore.Weak),
                candidate(2, PasswordScore.Excellent),
            ),
        )

        assertEquals(
            listOf(id(1)),
            outcome.findings.map { (it as HealthFinding.Item).id },
        )
    }

    @Test
    fun nothingToCheckFindsNothing() = runTest {
        val outcome = checker.check(emptyList())

        assertTrue(outcome.findings.isEmpty())
        assertNull(outcome.gap)
    }

    private fun id(n: Int) = UUID(0L, n.toLong())

    private fun candidate(n: Int, score: PasswordScore) =
        PasswordCandidate(id = id(n), score = score, password = "pw-$n".toCharArray())
}
