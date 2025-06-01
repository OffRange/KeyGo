package de.davis.keygo.core.domain.repository

import de.davis.keygo.core.domain.model.MainPassword

@Deprecated("Use auth domain instead")
interface MainPasswordRepository {

    suspend fun getMainPassword(): MainPassword

    suspend fun setMainPassword(mainPassword: MainPassword)
}