package de.davis.keygo.core.item.data.local.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import de.davis.keygo.core.item.data.local.entity.credential.TotpEntity
import de.davis.keygo.core.item.domain.alias.ItemId

@Dao
internal interface TotpDao {

    @Upsert
    suspend fun upsert(totp: TotpEntity)

    @Query("DELETE FROM totp WHERE login_id = :loginId")
    suspend fun delete(loginId: ItemId)

    @Query("SELECT * FROM totp WHERE login_id = :loginId")
    suspend fun getTotp(loginId: ItemId): TotpEntity?
}