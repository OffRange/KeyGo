package de.davis.passwordmanager.database.entities

import android.content.Context
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import de.davis.passwordmanager.database.ElementType
import de.davis.passwordmanager.database.TAG_PREFIX
import de.davis.passwordmanager.gson.annotations.Exclude

@Entity(indices = [Index("name", unique = true)])
data class Tag @JvmOverloads constructor(
    val name: String,
    @Exclude @PrimaryKey(autoGenerate = true) val tagId: Long = 0
)

fun Collection<Tag>.onlyCustoms(): Collection<Tag> {
    return filter { !it.shouldBeProtected }
}

val Tag.shouldBeProtected get() = this.name.startsWith(TAG_PREFIX)

val CharSequence.isProtectedTagName get() = startsWith(TAG_PREFIX)

fun Tag.getLocalizedName(context: Context) =
    if (shouldBeProtected) context.getString(ElementType.entries.first { e -> e.tag.name == name }.title) else name