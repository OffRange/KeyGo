package de.davis.keygo.core.item.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import de.davis.keygo.core.item.data.local.entity.credential.TotpEntity
import de.davis.keygo.core.item.domain.alias.ItemId

@Dao
internal interface TotpDao {

    @Upsert
    suspend fun upsert(totp: TotpEntity)

    @Query("SELECT * FROM totp WHERE login_id = :loginId")
    suspend fun getTotp(loginId: ItemId): TotpEntity?
}