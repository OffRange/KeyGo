package de.davis.keygo.core.item.domain.repository

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.Totp

interface TotpRepository {

    suspend fun getTotp(loginId: ItemId): Totp?
}