package de.davis.keygo.feature.password_health.domain.model

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.PasswordScore
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

data class StoredHealthReport(
    val items: List<StoredHealthItem>,
    val relationalFindings: List<HealthFinding.Relation>,
    val gaps: Map<CheckKind, CheckGap> = emptyMap(),
    val generatedAt: Instant,
    val breachCheckEnabled: Boolean,
) {

    val totalPasswordsScanned = items.size

    fun isFreshFor(
        fingerprints: Map<ItemId, HealthFingerprint>,
        breachCheckEnabled: Boolean,
        now: Instant,
    ): Boolean = this.breachCheckEnabled == breachCheckEnabled
            && now - generatedAt < TTL
            && items.associate { it.id to it.fingerprint } == fingerprints

    companion object {
        const val ALGORITHM_VERSION = 1
        val TTL = 1.days
    }
}

data class StoredHealthItem(
    val id: ItemId,
    val fingerprint: HealthFingerprint,
    val score: PasswordScore?,
    val breachOccurrences: Int,
    val unreadable: Boolean,
)

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
