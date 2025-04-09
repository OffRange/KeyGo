package de.davis.keygo.core.domain.repository

import de.davis.keygo.core.domain.model.MainPassword

interface MainPasswordRepository {

    suspend fun getMainPassword(): MainPassword

    suspend fun setMainPassword(mainPassword: MainPassword)
}