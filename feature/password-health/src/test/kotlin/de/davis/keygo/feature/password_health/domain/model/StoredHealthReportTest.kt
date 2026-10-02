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
import kotlin.time.Duration.Companion.nanoseconds
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

    @Test
    fun aBreachLookupHoldsUntilJustBeforeItsTtl() {
        val result = breach(0, checkedAt)

        assertTrue(result.isCurrentAt(checkedAt + BreachResult.TTL - 1.nanoseconds))
        assertFalse(result.isCurrentAt(checkedAt + BreachResult.TTL))
    }

    @Test
    fun aBreachLookupFromTheFutureStillHolds() {
        assertTrue(breach(0, checkedAt).isCurrentAt(checkedAt - 1.days))
    }

    @Test
    fun isStaleWhileAPasswordWasNeverLookedUp() {
        val unchecked = report.copy(items = listOf(storedItem(ids[0]), report.items[1]))

        assertFalse(unchecked.isFreshFor(fingerprints, true, checkedAt))
    }

    @Test
    fun isStaleWhenALoginWasRemoved() {
        assertFalse(report.isCurrentFor(mapOf(ids[0] to fingerprint(ids[0])), true))
    }

    @Test
    fun isStaleWhenTheBreachCheckWasTurnedOn() {
        val disabled = report.copy(items = ids.map { storedItem(it) }, breachCheckEnabled = false)

        assertFalse(disabled.isCurrentFor(fingerprints, true))
    }

    @Test
    fun aDisabledCheckIsNoReasonToRescan() {
        val skipped = report.copy(
            gaps = mapOf(CheckKind.Similarity to CheckGap(GapReason.Disabled, setOf(ids[0]))),
        )

        assertTrue(skipped.isCurrentFor(fingerprints, true))
    }

    @Test
    fun isStaleWhenAnotherCheckCouldNotReachItsService() {
        val incomplete = report.copy(
            gaps = mapOf(CheckKind.Strength to CheckGap(GapReason.Unreachable, setOf(ids[0]))),
        )

        assertFalse(incomplete.isCurrentFor(fingerprints, true))
    }

    @Test
    fun aFailedBreachLookupIsRetriedWithoutTheOtherChecks() {
        val incomplete = report.copy(
            gaps = mapOf(CheckKind.Breach to CheckGap(GapReason.Failed, setOf(ids[0]))),
        )

        assertTrue(incomplete.isCurrentFor(fingerprints, true))
    }

    @Test
    fun anEmptyReportIsCurrentForAnEmptyVault() {
        val empty = report.copy(items = emptyList())

        assertTrue(empty.isFreshFor(emptyMap(), true, checkedAt))
    }

    @Test
    fun breachResultsSkipItemsThatWereNeverLookedUp() {
        val unchecked = report.copy(items = listOf(storedItem(ids[0])))

        assertEquals(emptyMap(), unchecked.breachResultsAt(fingerprints, checkedAt))
    }

    @Test
    fun breachResultsSkipLoginsThatAreGone() {
        assertEquals(
            mapOf(ids[1] to breach(0, checkedAt)),
            report.breachResultsAt(mapOf(ids[1] to fingerprint(ids[1])), checkedAt),
        )
    }

    @Test
    fun countsEveryItemAsScanned() {
        assertEquals(2, report.totalPasswordsScanned)
    }

    @Test
    fun fingerprintsCompareByContent() {
        assertEquals(HealthFingerprint(byteArrayOf(1, 2)), HealthFingerprint(byteArrayOf(1, 2)))
        assertEquals(
            HealthFingerprint(byteArrayOf(1, 2)).hashCode(),
            HealthFingerprint(byteArrayOf(1, 2)).hashCode(),
        )
        assertFalse(HealthFingerprint(byteArrayOf(1, 2)) == HealthFingerprint(byteArrayOf(2, 1)))
    }
}
