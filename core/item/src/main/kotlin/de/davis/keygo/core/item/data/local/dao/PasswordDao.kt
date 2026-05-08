package de.davis.keygo.core.item.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import de.davis.keygo.core.item.data.local.entity.credential.PasswordEntity
import de.davis.keygo.core.item.data.local.pojo.PasswordScoreProjection
import kotlinx.coroutines.flow.Flow

@Dao
internal interface PasswordDao {

    @Upsert
    suspend fun upsert(password: PasswordEntity)

    @Query("SELECT login_id AS id, password_score FROM password")
    fun observeScores(): Flow<List<PasswordScoreProjection>>
}
