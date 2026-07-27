package de.davis.keygo.migration.create_access.domain.repository

import de.davis.keygo.migration.create_access.domain.model.MainPassword

internal interface MainPasswordRepository {

    suspend fun getMainPassword(): MainPassword

    suspend fun clearMainPassword()
}