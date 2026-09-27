package de.davis.keygo.feature.password_health.data.mapper

import com.google.protobuf.kotlin.toByteString
import de.davis.keygo.feature.password_health.data.local.model.ProtoHealthNotificationState
import de.davis.keygo.feature.password_health.data.local.model.ProtoHealthReminder
import de.davis.keygo.feature.password_health.data.local.model.ProtoHealthSnapshot
import de.davis.keygo.feature.password_health.domain.model.HealthNotificationState
import de.davis.keygo.feature.password_health.domain.model.HealthReminder
import de.davis.keygo.feature.password_health.domain.model.HealthSnapshot
import de.davis.keygo.feature.password_health.domain.model.VaultFingerprint
import kotlin.time.Instant

internal fun ProtoHealthNotificationState.toDomain() = HealthNotificationState(
    snapshot = if (hasSnapshot()) snapshot.toDomain() else null,
    reminder = if (hasReminder()) reminder.toDomain() else null,
)

internal fun ProtoHealthSnapshot.toDomain() = HealthSnapshot(
    attentionCount = attentionCount,
    breachedCount = breachedCount,
    vault = VaultFingerprint(vaultFingerprint.toByteArray()),
)

internal fun ProtoHealthReminder.toDomain() = HealthReminder(
    attentionCount = attentionCount,
    breachedCount = breachedCount,
    sentAt = Instant.fromEpochMilliseconds(sentAtEpochMillis),
    repeats = repeats,
)

internal fun HealthSnapshot.toProto(): ProtoHealthSnapshot = ProtoHealthSnapshot.newBuilder()
    .setAttentionCount(attentionCount)
    .setBreachedCount(breachedCount)
    .setVaultFingerprint(vault.value.toByteString())
    .build()

internal fun HealthReminder.toProto(): ProtoHealthReminder = ProtoHealthReminder.newBuilder()
    .setAttentionCount(attentionCount)
    .setBreachedCount(breachedCount)
    .setSentAtEpochMillis(sentAt.toEpochMilliseconds())
    .setRepeats(repeats)
    .build()
