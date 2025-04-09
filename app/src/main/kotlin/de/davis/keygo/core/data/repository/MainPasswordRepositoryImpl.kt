package de.davis.keygo.core.data.repository

import androidx.datastore.core.DataStore
import de.davis.keygo.core.data.local.model.ProtoMainPassword
import de.davis.keygo.core.data.mapper.toDomain
import de.davis.keygo.core.data.mapper.toProto
import de.davis.keygo.core.domain.model.MainPassword
import de.davis.keygo.core.domain.repository.MainPasswordRepository
import kotlinx.coroutines.flow.first

class MainPasswordRepositoryImpl(
    private val dataStore: DataStore<ProtoMainPassword>
) : MainPasswordRepository {

    override suspend fun getMainPassword(): MainPassword = dataStore.data.first().toDomain()

    override suspend fun setMainPassword(mainPassword: MainPassword) {
        dataStore.updateData {
            mainPassword.toProto()
        }
    }
}