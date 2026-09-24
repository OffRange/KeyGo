package de.davis.keygo.feature.password_health.domain.model

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.PasswordScore
import kotlin.time.Instant

data class StoredHealthReport(
    val items: List<StoredHealthItem>,
    val relationalFindings: List<HealthFinding.Relation>,
    val gaps: Map<CheckKind, CheckGap> = emptyMap(),
    val generatedAt: Instant,
    val breachCheckEnabled: Boolean,
) {

    val totalPasswordsScanned = items.size

    companion object {
        const val ALGORITHM_VERSION = 1
    }
}

data class StoredHealthItem(
    val id: ItemId,
    val fingerprint: HealthFingerprint,
    val score: PasswordScore?,
    val breachOccurrences: Int,
    val unreadable: Boolean,
)

@JvmInline
value class HealthFingerprint(val value: ByteArray)
