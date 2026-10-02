package de.davis.keygo.feature.password_health.data.mapper

import android.util.Log
import com.google.protobuf.kotlin.toByteString
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.feature.password_health.data.local.model.ProtoCheckKind
import de.davis.keygo.feature.password_health.data.local.model.ProtoGapReason
import de.davis.keygo.feature.password_health.data.local.model.ProtoPasswordScore
import de.davis.keygo.feature.password_health.data.local.model.ProtoRelationFinding
import de.davis.keygo.feature.password_health.data.local.model.ProtoRelationType
import de.davis.keygo.feature.password_health.data.local.model.ProtoStoredHealthReport
import de.davis.keygo.feature.password_health.data.local.model.ProtoStoredItem
import de.davis.keygo.feature.password_health.data.local.model.protoCheckGap
import de.davis.keygo.feature.password_health.data.local.model.protoRelationFinding
import de.davis.keygo.feature.password_health.data.local.model.protoStoredHealthReport
import de.davis.keygo.feature.password_health.data.local.model.protoStoredItem
import de.davis.keygo.feature.password_health.domain.model.BreachResult
import de.davis.keygo.feature.password_health.domain.model.CheckGap
import de.davis.keygo.feature.password_health.domain.model.CheckKind
import de.davis.keygo.feature.password_health.domain.model.GapReason
import de.davis.keygo.feature.password_health.domain.model.HealthFinding
import de.davis.keygo.feature.password_health.domain.model.HealthFingerprint
import de.davis.keygo.feature.password_health.domain.model.RelationType
import de.davis.keygo.feature.password_health.domain.model.StoredHealthItem
import de.davis.keygo.feature.password_health.domain.model.StoredHealthReport
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

internal fun StoredHealthReport.toCompressedByteArray(): ByteArray =
    ByteArrayOutputStream().also { bos ->
        GZIPOutputStream(bos).use { it.write(toProto().toByteArray()) }
    }.toByteArray()

internal fun ByteArray.toStoredHealthReport(): StoredHealthReport? =
    runCatching {
        ProtoStoredHealthReport.parseFrom(GZIPInputStream(ByteArrayInputStream(this)).use { it.readBytes() })
            .toDomain()
    }.getOrNull()

private fun ProtoStoredHealthReport.toDomain(): StoredHealthReport? {
    if (algorithmVersion != StoredHealthReport.ALGORITHM_VERSION) return null

    val items = itemsList.map(ProtoStoredItem::toDomain)

    fun List<Int>.toItemIds(): Set<ItemId>? {
        return mapTo(HashSet(size)) { ref ->
            items.getOrNull(ref)?.id ?: run {
                Log.w("StoredHealthReportMapper", "Ref $ref out of range (${items.size} items)")
                return null
            }
        }
    }

    val gaps = HashMap<CheckKind, CheckGap>(gapsCount)
    gapsList.forEach { gap ->
        val kind = gap.kind.toDomain()
        val reason = gap.reason.toDomain()
        if (kind == null || reason == null) return@forEach

        val gapValue = CheckGap(
            reason = reason,
            unchecked = gap.uncheckedItemRefsList.toItemIds() ?: return null,
        )
        if (gaps.put(kind, gapValue) != null) {
            Log.w("StoredHealthReportMapper", "Duplicate gap for $kind")
            return null
        }
    }

    return StoredHealthReport(
        items = items,
        relationalFindings = relationFindingList.mapNotNull {
            it.toDomain(
                it.itemRefsList.toItemIds() ?: return null
            )
        },
        gaps = gaps,
        breachCheckEnabled = breachCheckEnabled,
    )
}

private fun StoredHealthReport.toProto(): ProtoStoredHealthReport {
    val refOf = HashMap<ItemId, Int>(items.size)
    items.forEachIndexed { index, item ->
        check(refOf.put(item.id, index) == null) { "Duplicate item ${item.id}" }
    }
    fun Set<ItemId>.toRefs(): List<Int> = map { id ->
        refOf[id] ?: error("Finding references $id, which is not in items")
    }

    return protoStoredHealthReport {
        items += this@toProto.items.map(StoredHealthItem::toProto)
        relationFinding += this@toProto.relationalFindings.map { it.toProto(it.relatedItemIds.toRefs()) }
        gaps += this@toProto.gaps.toProto { it.unchecked.toRefs() }
        breachCheckEnabled = this@toProto.breachCheckEnabled
        algorithmVersion = StoredHealthReport.ALGORITHM_VERSION
    }
}

private fun StoredHealthItem.toProto() = protoStoredItem {
    id = this@toProto.id.toProtoByteString()
    fingerprint = this@toProto.fingerprint.value.toByteString()
    score = this@toProto.score.toProto()

    breach?.let { breach ->
        if (breach.occurrences > 0) breachOccurrences = breach.occurrences
        breachCheckedAtEpochMillis = breach.checkedAt.toEpochMilliseconds()
    }

    unreadable = this@toProto.unreadable
}

private fun ProtoStoredItem.toDomain(): StoredHealthItem = StoredHealthItem(
    id = Uuid.fromByteArray(id.toByteArray()).toJavaUuid(),
    fingerprint = HealthFingerprint(fingerprint.toByteArray()),
    score = score.toDomain(),
    breach = if (hasBreachCheckedAtEpochMillis()) BreachResult(
        occurrences = breachOccurrences,
        checkedAt = Instant.fromEpochMilliseconds(breachCheckedAtEpochMillis),
    ) else null,
    unreadable = unreadable,
)

private fun HealthFinding.Relation.toProto(refs: List<Int>) = protoRelationFinding {
    itemRefs += refs
    type = this@toProto.type.toProto()
}

private fun ProtoRelationFinding.toDomain(relatedItemIds: Set<ItemId>) = type.toDomain()?.let {
    HealthFinding.Relation(
        relatedItemIds = relatedItemIds,
        type = it,
    )
}

private fun Map<CheckKind, CheckGap>.toProto(refs: (CheckGap) -> List<Int>) =
    map { (checkKind, gap) ->
        protoCheckGap {
            kind = checkKind.toProto()
            reason = gap.reason.toProto()
            uncheckedItemRefs += refs(gap)
        }
    }

private fun PasswordScore?.toProto() = when (this) {
    null,
    PasswordScore.None -> ProtoPasswordScore.PASSWORD_SCORE_NONE

    PasswordScore.Ridiculous -> ProtoPasswordScore.PASSWORD_SCORE_RIDICULOUS
    PasswordScore.Weak -> ProtoPasswordScore.PASSWORD_SCORE_WEAK
    PasswordScore.Moderate -> ProtoPasswordScore.PASSWORD_SCORE_MODERATE
    PasswordScore.Strong -> ProtoPasswordScore.PASSWORD_SCORE_STRONG
    PasswordScore.Excellent -> ProtoPasswordScore.PASSWORD_SCORE_EXCELLENT
}

private fun ProtoPasswordScore.toDomain() = when (this) {
    ProtoPasswordScore.UNRECOGNIZED,
    ProtoPasswordScore.PASSWORD_SCORE_NONE -> null

    ProtoPasswordScore.PASSWORD_SCORE_RIDICULOUS -> PasswordScore.Ridiculous
    ProtoPasswordScore.PASSWORD_SCORE_WEAK -> PasswordScore.Weak
    ProtoPasswordScore.PASSWORD_SCORE_MODERATE -> PasswordScore.Moderate
    ProtoPasswordScore.PASSWORD_SCORE_STRONG -> PasswordScore.Strong
    ProtoPasswordScore.PASSWORD_SCORE_EXCELLENT -> PasswordScore.Excellent
}

private fun RelationType.toProto() = when (this) {
    RelationType.Reused -> ProtoRelationType.RELATION_TYPE_REUSED
    RelationType.Similar -> ProtoRelationType.RELATION_TYPE_SIMILAR
}

private fun ProtoRelationType.toDomain() = when (this) {
    ProtoRelationType.UNRECOGNIZED -> null

    ProtoRelationType.RELATION_TYPE_REUSED -> RelationType.Reused
    ProtoRelationType.RELATION_TYPE_SIMILAR -> RelationType.Similar
}

private fun CheckKind.toProto() = when (this) {
    CheckKind.Breach -> ProtoCheckKind.CHECK_KIND_BREACH
    CheckKind.Reuse -> ProtoCheckKind.CHECK_KIND_REUSE
    CheckKind.Strength -> ProtoCheckKind.CHECK_KIND_STRENGTH
    CheckKind.Similarity -> ProtoCheckKind.CHECK_KIND_SIMILARITY
}

private fun ProtoCheckKind.toDomain() = when (this) {
    ProtoCheckKind.UNRECOGNIZED -> null

    ProtoCheckKind.CHECK_KIND_BREACH -> CheckKind.Breach
    ProtoCheckKind.CHECK_KIND_REUSE -> CheckKind.Reuse
    ProtoCheckKind.CHECK_KIND_STRENGTH -> CheckKind.Strength
    ProtoCheckKind.CHECK_KIND_SIMILARITY -> CheckKind.Similarity
}

private fun GapReason.toProto() = when (this) {
    GapReason.Disabled -> ProtoGapReason.GAP_REASON_DISABLED
    GapReason.Failed -> ProtoGapReason.GAP_REASON_FAILED
    GapReason.Unreachable -> ProtoGapReason.GAP_REASON_UNREACHABLE
}

private fun ProtoGapReason.toDomain() = when (this) {
    ProtoGapReason.UNRECOGNIZED -> null

    ProtoGapReason.GAP_REASON_DISABLED -> GapReason.Disabled
    ProtoGapReason.GAP_REASON_FAILED -> GapReason.Failed
    ProtoGapReason.GAP_REASON_UNREACHABLE -> GapReason.Unreachable
}

private fun ItemId.toProtoByteString() = toKotlinUuid().toByteArray().toByteString()
