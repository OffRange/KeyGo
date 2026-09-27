package de.davis.keygo.feature.password_health.domain.model

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.PasswordScore
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

data class StoredHealthReport(
    val items: List<StoredHealthItem>,
    val relationalFindings: List<HealthFinding.Relation>,
    val gaps: Map<CheckKind, CheckGap> = emptyMap(),
    val breachCheckEnabled: Boolean,
) {

    val totalPasswordsScanned = items.size

    fun isFreshFor(
        fingerprints: Map<ItemId, HealthFingerprint>,
        breachCheckEnabled: Boolean,
        now: Instant,
    ): Boolean = isCurrentFor(fingerprints, breachCheckEnabled)
            && (!breachCheckEnabled || items.all { it.breach?.isCurrentAt(now) == true })

    fun isCurrentFor(
        fingerprints: Map<ItemId, HealthFingerprint>,
        breachCheckEnabled: Boolean,
    ): Boolean = this.breachCheckEnabled == breachCheckEnabled
            && items.none { it.unreadable } // if any item was unreadable, we want a new report
            && !hasGapNeedingFullScan
            && items.associate { it.id to it.fingerprint } == fingerprints

    fun breachResultsAt(
        fingerprints: Map<ItemId, HealthFingerprint>,
        now: Instant,
    ): Map<ItemId, BreachResult> = items
        .filter { fingerprints[it.id] == it.fingerprint }
        .mapNotNull { item -> item.breach?.takeIf { it.isCurrentAt(now) }?.let { item.id to it } }
        .toMap()

    // Breach gaps are left out: their items have no BreachResult, so the scanner looks up just
    // those again in refreshBreaches instead of rescanning everything.
    private val hasGapNeedingFullScan: Boolean
        get() = gaps
            .filterKeys { it != CheckKind.Breach }
            .values
            .any { it.reason != GapReason.Disabled }

    companion object {
        const val ALGORITHM_VERSION = 1
    }
}

data class StoredHealthItem(
    val id: ItemId,
    val fingerprint: HealthFingerprint,
    val score: PasswordScore?,
    val breach: BreachResult?,
    val unreadable: Boolean,
)

data class BreachResult(
    val occurrences: Int,
    val checkedAt: Instant,
) {

    fun isCurrentAt(now: Instant): Boolean = now - checkedAt < TTL

    companion object {
        val TTL = 1.days
    }
}

class HealthFingerprint(val value: ByteArray) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HealthFingerprint

        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        return value.contentHashCode()
    }
}
