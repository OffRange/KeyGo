package de.davis.keygo.feature.password_health.domain.model

import de.davis.keygo.feature.password_health.domain.report.breach
import de.davis.keygo.feature.password_health.domain.report.fingerprint
import de.davis.keygo.feature.password_health.domain.report.id
import de.davis.keygo.feature.password_health.domain.report.storedItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class StoredHealthReportTest {

    private val checkedAt = Instant.fromEpochSeconds(1_000_000)
    private val ids = List(2) { id(it) }
    private val fingerprints = ids.associateWith(::fingerprint)
    private val report = StoredHealthReport(
        items = ids.map { storedItem(it, breach = breach(0, checkedAt)) },
        relationalFindings = emptyList(),
        breachCheckEnabled = true,
    )

    @Test
    fun isFreshForTheSameLoginsWhileTheBreachLookupsHold() {
        assertTrue(report.isFreshFor(fingerprints, true, checkedAt + 1.seconds))
    }

    @Test
    fun isStaleOnceABreachLookupRunsOut() {
        assertFalse(report.isFreshFor(fingerprints, true, checkedAt + BreachResult.TTL))
    }

    @Test
    fun anExpiredBreachLookupLeavesTheOtherChecksCurrent() {
        assertTrue(report.isCurrentFor(fingerprints, true))
    }

    @Test
    fun withoutTheBreachCheckItNeverExpires() {
        val disabled = report.copy(
            items = ids.map { storedItem(it) },
            gaps = mapOf(CheckKind.Breach to CheckGap(GapReason.Disabled, ids.toSet())),
            breachCheckEnabled = false,
        )

        assertTrue(disabled.isFreshFor(fingerprints, false, checkedAt + 365.days))
    }

    @Test
    fun isStaleWhenAPasswordChanged() {
        val changed = fingerprints + (ids[0] to HealthFingerprint(byteArrayOf(1)))

        assertFalse(report.isCurrentFor(changed, true))
    }

    @Test
    fun isStaleWhenALoginWasAdded() {
        val added = fingerprints + (id(9) to fingerprint(id(9)))

        assertFalse(report.isCurrentFor(added, true))
    }

    @Test
    fun isStaleWhenTheBreachCheckWasToggled() {
        assertFalse(report.isCurrentFor(fingerprints, false))
    }

    @Test
    fun isStaleWhileAPasswordCouldNotBeRead() {
        val unreadable = report.copy(items = listOf(storedItem(ids[0], unreadable = true)))

        assertFalse(unreadable.isCurrentFor(mapOf(ids[0] to fingerprint(ids[0])), true))
    }

    @Test
    fun anUnreachableBreachLookupIsRetriedWithoutTheOtherChecks() {
        val incomplete = report.copy(
            items = listOf(storedItem(ids[0]), report.items[1]),
            gaps = mapOf(CheckKind.Breach to CheckGap(GapReason.Unreachable, setOf(ids[0]))),
        )

        assertFalse(incomplete.isFreshFor(fingerprints, true, checkedAt))
        assertTrue(incomplete.isCurrentFor(fingerprints, true))
    }

    @Test
    fun isStaleWhenAnotherCheckFailed() {
        val incomplete = report.copy(
            gaps = mapOf(CheckKind.Similarity to CheckGap(GapReason.Failed, setOf(ids[0]))),
        )

        assertFalse(incomplete.isCurrentFor(fingerprints, true))
    }

    @Test
    fun breachResultsOnlyCarryOverForUnchangedPasswordsStillInDate() {
        val mixed = report.copy(
            items = listOf(
                storedItem(ids[0], breach = breach(2, checkedAt)),
                storedItem(ids[1], breach = breach(0, checkedAt - BreachResult.TTL)),
                storedItem(id(2), breach = breach(5, checkedAt)),
            ),
        )
        val current = fingerprints + (id(2) to HealthFingerprint(byteArrayOf(1)))

        assertEquals(
            mapOf(ids[0] to breach(2, checkedAt)),
            mixed.breachResultsAt(current, checkedAt)
        )
    }
}
