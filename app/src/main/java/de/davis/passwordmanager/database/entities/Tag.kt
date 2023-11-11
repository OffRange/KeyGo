package de.davis.passwordmanager.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import de.davis.passwordmanager.gson.annotations.Exclude

@Entity(indices = [Index("name", unique = true)])
data class Tag @JvmOverloads constructor(
    val name: String,
    @Exclude @PrimaryKey(autoGenerate = true) val tagId: Int = 0
)
