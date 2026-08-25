package de.davis.keygo.core.item.data.local.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import de.davis.keygo.core.item.data.local.entity.credential.PasswordEntity
import de.davis.keygo.core.item.data.local.pojo.PasswordScoreProjection
import de.davis.keygo.core.item.domain.alias.ItemId
import kotlinx.coroutines.flow.Flow

@Dao
internal interface PasswordDao {

    @Upsert
    suspend fun upsert(password: PasswordEntity)

    @Query("DELETE FROM password WHERE login_id = :loginId")
    suspend fun delete(loginId: ItemId)

    @Query("SELECT login_id AS id, password_score FROM password")
    fun observeScores(): Flow<List<PasswordScoreProjection>>
}
