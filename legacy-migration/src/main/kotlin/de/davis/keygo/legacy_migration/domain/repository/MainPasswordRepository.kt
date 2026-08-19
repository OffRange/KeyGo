package de.davis.keygo.legacy_migration.domain.repository

import de.davis.keygo.legacy_migration.domain.model.MainPassword

internal interface MainPasswordRepository {

    suspend fun getMainPassword(): MainPassword

    suspend fun clearMainPassword()
}
