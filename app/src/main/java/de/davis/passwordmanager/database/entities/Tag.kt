package de.davis.passwordmanager.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import de.davis.passwordmanager.database.TAG_PREFIX
import de.davis.passwordmanager.gson.annotations.Exclude

@Entity(indices = [Index("name", unique = true)])
class Tag @JvmOverloads constructor(
    val name: String,
    @Exclude @PrimaryKey(autoGenerate = true) val tagId: Long = 0
)

fun Collection<Tag>.onlyCustoms(): Collection<Tag> {
    return filter { !it.name.startsWith(TAG_PREFIX) }
}

val Tag.shouldBeProtected get() = this.name.startsWith(TAG_PREFIX)