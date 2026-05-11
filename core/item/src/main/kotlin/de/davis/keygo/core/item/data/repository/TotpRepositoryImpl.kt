package de.davis.keygo.core.item.data.repository

import de.davis.keygo.core.item.data.local.dao.TotpDao
import de.davis.keygo.core.item.data.mapper.toDomain
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.Totp
import de.davis.keygo.core.item.domain.repository.TotpRepository
import org.koin.core.annotation.Single

@Single
internal class TotpRepositoryImpl(
    private val totpDao: TotpDao
) : TotpRepository {

    override suspend fun getTotp(loginId: ItemId): Totp? = totpDao.getTotp(loginId)?.toDomain()
}