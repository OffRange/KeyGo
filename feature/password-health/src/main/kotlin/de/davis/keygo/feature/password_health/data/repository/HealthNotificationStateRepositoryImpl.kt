package de.davis.keygo.feature.password_health.data.repository

import androidx.datastore.core.DataStore
import de.davis.keygo.feature.password_health.data.local.model.ProtoHealthNotificationState
import de.davis.keygo.feature.password_health.data.mapper.toDomain
import de.davis.keygo.feature.password_health.data.mapper.toProto
import de.davis.keygo.feature.password_health.di.annotation.HealthNotificationStateQualifier
import de.davis.keygo.feature.password_health.domain.model.HealthNotificationState
import de.davis.keygo.feature.password_health.domain.model.HealthReminder
import de.davis.keygo.feature.password_health.domain.model.HealthSnapshot
import de.davis.keygo.feature.password_health.domain.repository.HealthNotificationStateRepository
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single

@Single
internal class HealthNotificationStateRepositoryImpl(
    @HealthNotificationStateQualifier
    private val dataStore: DataStore<ProtoHealthNotificationState>,
) : HealthNotificationStateRepository {

    override suspend fun get(): HealthNotificationState = dataStore.data.first().toDomain()

    override suspend fun setSnapshot(snapshot: HealthSnapshot?) {
        dataStore.updateData {
            if (snapshot == null) it.toBuilder().clearSnapshot().build()
            else it.toBuilder().setSnapshot(snapshot.toProto()).build()
        }
    }

    override suspend fun setReminder(reminder: HealthReminder?) {
        dataStore.updateData {
            if (reminder == null) it.toBuilder().clearReminder().build()
            else it.toBuilder().setReminder(reminder.toProto()).build()
        }
    }

    override suspend fun clear() {
        dataStore.updateData { ProtoHealthNotificationState.getDefaultInstance() }
    }
}
