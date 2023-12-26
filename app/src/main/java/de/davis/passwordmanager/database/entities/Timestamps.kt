package de.davis.passwordmanager.database.entities

import androidx.room.ColumnInfo
import java.util.Date

data class Timestamps(
    @ColumnInfo(name = "created_at", defaultValue = "CURRENT_TIMESTAMP") var createdAt: Date?,
    @ColumnInfo(name = "modified_at") var modifiedAt: Date? = null
) {

    init {
        if (createdAt == null)
            createdAt = Date()
    }

    companion object {
        val CURRENT get() = Timestamps(Date())
    }
}
