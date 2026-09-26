package de.davis.keygo.feature.password_health.domain.checker

import de.davis.keygo.feature.password_health.FakeBreachedRepository
import de.davis.keygo.feature.password_health.FakeConnectivityRepository
import de.davis.keygo.feature.password_health.FakeHealthSettingsRepository
import de.davis.keygo.feature.password_health.domain.model.BreachedError
import de.davis.keygo.feature.password_health.domain.model.CheckGap
import de.davis.keygo.feature.password_health.domain.model.CheckKind
import de.davis.keygo.feature.password_health.domain.model.GapReason
import de.davis.keygo.feature.password_health.domain.model.HealthFinding
import de.davis.keygo.feature.password_health.domain.model.ItemIssue
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Robolectric only so the gap warnings can reach android.util.Log.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BreachedPasswordCheckTest {

    private val breached = FakeBreachedRepository()
    private val state = FakeHealthSettingsRepository(breachesEnabled = true)
    private val connectivity = FakeConnectivityRepository(online = true)
    private val checker = BreachedPasswordCheck(breached, state, connectivity)

    @Test
    fun reportsAsTheBreachCheck() {
        assertEquals(CheckKind.Breach, checker.type)
    }

    @Test
    fun asksForTheRangeOfTheUpperCaseSha1() = runTest {
        checker.check(candidates("password"))

        assertEquals(
            listOf(
                FakeBreachedRepository.Call(
                    "5BAA6",
                    setOf("1E4C9B93F3F0682250B6CF8331B7EE68FD8")
                )
            ),
            breached.calls,
        )
    }

    @Test
    fun hashesThePasswordAsUtf8() = runTest {
        checker.check(candidates("pässwörd"))

        assertEquals(
            FakeBreachedRepository.Call("F517D", setOf("DF1D32A112FF1AD55C66D1B12CB38E7E8F7")),
            breached.calls.single(),
        )
    }

    @Test
    fun aBreachedPasswordIsFlaggedWithItsOccurrences() = runTest {
        breached.breaches = mapOf(PASSWORD to 42)

        val outcome = checker.check(candidates("password", "hunter2"))

        assertEquals(
            listOf(HealthFinding.Item(id(0), ItemIssue.Breached(occurrences = 42))),
            outcome.findings,
        )
        assertNull(outcome.gap)
    }

    @Test
    fun aCleanVaultHasNoFindingsAndNoGap() = runTest {
        val outcome = checker.check(candidates("password", "hunter2"))

        assertTrue(outcome.findings.isEmpty())
        assertNull(outcome.gap)
    }

    @Test
    fun everyLoginSharingABreachedPasswordIsFlagged() = runTest {
        breached.breaches = mapOf(PASSWORD to 7)

        val outcome = checker.check(candidates("password", "password"))

        assertEquals(
            setOf(
                HealthFinding.Item(id(0), ItemIssue.Breached(7)),
                HealthFinding.Item(id(1), ItemIssue.Breached(7)),
            ),
            outcome.findings.toSet(),
        )
    }

    @Test
    fun aSharedPasswordIsLookedUpOnce() = runTest {
        checker.check(candidates("password", "password", "password"))

        assertEquals(1, breached.calls.size)
        assertEquals(1, breached.calls.single().suffixes.size)
    }

    @Test
    fun eachRangeIsAskedForSeparately() = runTest {
        checker.check(candidates("password", "hunter2"))

        assertEquals(setOf("5BAA6", "F3BBB"), breached.calls.mapTo(mutableSetOf()) { it.prefix })
    }

    @Test
    fun nothingToCheckAsksNobody() = runTest {
        val outcome = checker.check(emptyList())

        assertTrue(breached.calls.isEmpty())
        assertTrue(outcome.findings.isEmpty())
        assertNull(outcome.gap)
    }

    @Test
    fun aDisabledCheckSkipsEveryPasswordWithoutAsking() = runTest {
        state.setBreachEnabled(false)

        val outcome = checker.check(candidates("password", "hunter2"))

        assertTrue(breached.calls.isEmpty())
        assertTrue(outcome.findings.isEmpty())
        assertEquals(CheckGap(GapReason.Disabled, setOf(id(0), id(1))), outcome.gap)
    }

    @Test
    fun noInternetSkipsEveryPasswordWithoutAsking() = runTest {
        connectivity.online = false

        val outcome = checker.check(candidates("password", "hunter2"))

        assertTrue(breached.calls.isEmpty())
        assertEquals(CheckGap(GapReason.Unreachable, setOf(id(0), id(1))), outcome.gap)
    }

    @Test
    fun anUnreachableRangeLeavesItsPasswordsUnchecked() = runTest {
        breached.failures["5BAA6"] = BreachedError.Unreachable

        val outcome = checker.check(candidates("password", "hunter2"))

        assertEquals(CheckGap(GapReason.Unreachable, setOf(id(0))), outcome.gap)
    }

    @Test
    fun aFailedApiAnswerIsAFailedGap() = runTest {
        breached.failures["5BAA6"] = BreachedError.ApiFailed

        val outcome = checker.check(candidates("password"))

        assertEquals(CheckGap(GapReason.Failed, setOf(id(0))), outcome.gap)
    }

    @Test
    fun anInvalidPrefixIsAFailedGap() = runTest {
        breached.failures["5BAA6"] = BreachedError.InvalidPrefix

        val outcome = checker.check(candidates("password"))

        assertEquals(CheckGap(GapReason.Failed, setOf(id(0))), outcome.gap)
    }

    @Test
    fun unreachableWinsOverAFailureInTheSameRun() = runTest {
        breached.failures["5BAA6"] = BreachedError.ApiFailed
        breached.failures["F3BBB"] = BreachedError.Unreachable

        val outcome = checker.check(candidates("password", "hunter2"))

        assertEquals(CheckGap(GapReason.Unreachable, setOf(id(0), id(1))), outcome.gap)
    }

    @Test
    fun aFailedRangeKeepsTheFindingsOfTheRangesThatAnswered() = runTest {
        breached.breaches = mapOf(HUNTER2 to 3)
        breached.failures["5BAA6"] = BreachedError.Unreachable

        val outcome = checker.check(candidates("password", "hunter2"))

        assertEquals(listOf(HealthFinding.Item(id(1), ItemIssue.Breached(3))), outcome.findings)
        assertEquals(CheckGap(GapReason.Unreachable, setOf(id(0))), outcome.gap)
    }

    @Test
    fun aFailedRangeLeavesEveryLoginSharingItsPasswordUnchecked() = runTest {
        breached.failures["5BAA6"] = BreachedError.ApiFailed

        val outcome = checker.check(candidates("password", "password"))

        assertEquals(CheckGap(GapReason.Failed, setOf(id(0), id(1))), outcome.gap)
    }

    @Test
    fun thePasswordsAreLeftForTheCallerToWipe() = runTest {
        val candidates = candidates("password")

        checker.check(candidates)

        assertEquals("password", candidates.single().password.concatToString())
    }

    private fun id(n: Int) = UUID(0L, n.toLong())

    private companion object {
        const val PASSWORD = "5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8"
        const val HUNTER2 = "F3BBBD66A63D4BF1747940578EC3D0103530E21D"
    }
}
