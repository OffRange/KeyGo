package de.davis.keygo.auth.data.repository

import de.davis.keygo.auth.domain.repository.DeviceInfoRepository
import org.koin.core.annotation.Single

@Single
class DeviceInfoRepositoryImpl : DeviceInfoRepository {

    override fun getNumCors(): Int = Runtime.getRuntime().availableProcessors()
}