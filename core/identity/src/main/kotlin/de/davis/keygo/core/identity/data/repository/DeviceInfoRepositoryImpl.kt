package de.davis.keygo.core.identity.data.repository

import de.davis.keygo.core.identity.domain.repository.DeviceInfoRepository
import org.koin.core.annotation.Single

@Single
internal class DeviceInfoRepositoryImpl : DeviceInfoRepository {

    override fun getNumCors(): Int = Runtime.getRuntime().availableProcessors()
}