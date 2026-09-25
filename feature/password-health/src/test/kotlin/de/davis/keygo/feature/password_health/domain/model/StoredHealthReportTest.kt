package de.davis.keygo.feature.password_health.domain.model

import de.davis.keygo.core.item.domain.alias.ItemId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class StoredHealthReportTest {

    private val generatedAt = Instant.fromEpochSeconds(1_000_000)
    private val ids = List(2) { UUID(0L, it.toLong()) }
    private val fingerprints = ids.associateWith(::fingerprint)
    private val report = StoredHealthReport(
        items = ids.map {
            StoredHealthItem(
                id = it,
                fingerprint = fingerprint(it),
                score = null,
                breachOccurrences = 0,
                unreadable = false,
            )
        },
        relationalFindings = emptyList(),
        generatedAt = generatedAt,
        breachCheckEnabled = true,
    )

    @Test
    fun isFreshForTheSameLoginsWithinItsLifetime() {
        assertTrue(report.isFreshFor(fingerprints, true, generatedAt + 1.seconds))
    }

    @Test
    fun isStaleOnceItsLifetimeRunsOut() {
        assertFalse(report.isFreshFor(fingerprints, true, generatedAt + StoredHealthReport.TTL))
    }

    @Test
    fun isStaleWhenAPasswordChanged() {
        val changed = fingerprints + (ids[0] to HealthFingerprint(byteArrayOf(1)))

        assertFalse(report.isFreshFor(changed, true, generatedAt))
    }

    @Test
    fun isStaleWhenALoginWasAdded() {
        val added = fingerprints + (UUID(0L, 9L) to fingerprint(UUID(0L, 9L)))

        assertFalse(report.isFreshFor(added, true, generatedAt))
    }

    @Test
    fun isStaleWhenTheBreachCheckWasToggled() {
        assertFalse(report.isFreshFor(fingerprints, false, generatedAt))
    }

    @Test
    fun isStaleWhenACheckCouldNotReachItsService() {
        val incomplete = report.copy(
            gaps = mapOf(CheckKind.Breach to CheckGap(GapReason.Unreachable, setOf(ids[0]))),
        )

        assertFalse(incomplete.isFreshFor(fingerprints, true, generatedAt))
    }

    @Test
    fun isStaleWhenACheckFailed() {
        val incomplete = report.copy(
            gaps = mapOf(CheckKind.Breach to CheckGap(GapReason.Failed, setOf(ids[0]))),
        )

        assertFalse(incomplete.isFreshFor(fingerprints, true, generatedAt))
    }

    @Test
    fun staysFreshWhenACheckWasTurnedOff() {
        val disabled = report.copy(
            gaps = mapOf(CheckKind.Breach to CheckGap(GapReason.Disabled, ids.toSet())),
            breachCheckEnabled = false,
        )

        assertTrue(disabled.isFreshFor(fingerprints, false, generatedAt))
    }

    private fun fingerprint(id: ItemId) = HealthFingerprint(id.toString().encodeToByteArray())
}
