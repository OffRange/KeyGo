package de.davis.keygo.migration.create_access.data.repository

import androidx.datastore.core.DataStore
import com.google.protobuf.timestamp
import de.davis.keygo.migration.create_access.data.local.model.ProtoMainPassword
import de.davis.keygo.migration.create_access.data.local.model.copy
import de.davis.keygo.migration.create_access.data.mapper.toDomain
import de.davis.keygo.migration.create_access.di.annotation.MainPasswordQualifier
import de.davis.keygo.migration.create_access.domain.model.MainPassword
import de.davis.keygo.migration.create_access.domain.repository.MainPasswordRepository
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single

@Single
internal class MainPasswordRepositoryImpl(
    @param:MainPasswordQualifier
    private val dataStore: DataStore<ProtoMainPassword>,
) : MainPasswordRepository {

    override suspend fun getMainPassword(): MainPassword = dataStore.data.first().toDomain()

    override suspend fun clearMainPassword() {
        dataStore.updateData {
            it.copy {
                hash = ""
                createdAt = timestamp {
                    seconds = 0
                    nanos = 0
                }
            }
        }
    }
}
