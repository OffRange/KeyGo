package de.davis.keygo.feature.password_health.domain.checker

import de.davis.keygo.feature.password_health.domain.model.CheckKind
import de.davis.keygo.feature.password_health.domain.model.HealthFinding
import de.davis.keygo.feature.password_health.domain.model.RelationType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReusePasswordCheckTest {

    private val checker = ReusePasswordCheck()

    @Test
    fun reportsAsTheReuseCheck() {
        assertEquals(CheckKind.Reuse, checker.type)
    }

    @Test
    fun flagsTwoLoginsSharingAPassword() = runTest {
        assertEquals(setOf(setOf("hunter2")), reused("hunter2", "hunter2", "other-password"))
    }

    @Test
    fun everyLoginSharingThePasswordIsInTheOneFinding() = runTest {
        val candidates = candidates("same", "same", "same")

        val findings = ReusePasswordCheck().check(candidates).findings

        assertEquals(
            listOf(
                HealthFinding.Relation(
                    relatedItemIds = candidates.mapTo(mutableSetOf()) { it.id },
                    type = RelationType.Reused,
                ),
            ),
            findings,
        )
    }

    @Test
    fun separateSharedPasswordsAreSeparateFindings() = runTest {
        assertEquals(setOf(setOf("alpha"), setOf("beta")), reused("alpha", "beta", "alpha", "beta"))
    }

    @Test
    fun uniquePasswordsAreNotReused() = runTest {
        assertTrue(reused("one", "two", "three").isEmpty())
    }

    @Test
    fun passwordsDifferingOnlyInCaseAreNotReused() = runTest {
        assertTrue(reused("Password", "password").isEmpty())
    }

    @Test
    fun passwordsDifferingOnlyInTrailingWhitespaceAreNotReused() = runTest {
        assertTrue(reused("password", "password ").isEmpty())
    }

    @Test
    fun sharedNonAsciiPasswordsAreReused() = runTest {
        assertEquals(setOf(setOf("pässwörd🔑")), reused("pässwörd🔑", "pässwörd🔑"))
    }

    @Test
    fun aSinglePasswordIsNotReused() = runTest {
        assertTrue(reused("lonely").isEmpty())
    }

    @Test
    fun nothingToCheckFindsNothing() = runTest {
        val outcome = checker.check(emptyList())

        assertTrue(outcome.findings.isEmpty())
        assertNull(outcome.gap)
    }

    /** The reused groups, as the passwords themselves collapsed to one per group. */
    private suspend fun reused(vararg passwords: String): Set<Set<String>> {
        val candidates = candidates(*passwords)
        val byId = candidates.associate { it.id to it.password.concatToString() }

        return checker.check(candidates).findings
            .map { assertRelation(it) }
            .mapTo(mutableSetOf()) { ids -> ids.mapTo(mutableSetOf()) { byId.getValue(it) } }
    }

    private fun assertRelation(finding: HealthFinding) =
        (finding as HealthFinding.Relation).also { assertEquals(RelationType.Reused, it.type) }
            .relatedItemIds
}
