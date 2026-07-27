package de.davis.keygo.migration.legacy_data.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * v1's `SecureElement`, ported so the legacy database can be read through Room instead of raw SQL.
 *
 * The class shape is deliberately copied from v1 rather than tidied up. Room emits direct fields
 * before embedded ones, so v1's declaration order (title, data, favorite, embedded timestamps, id,
 * then the body property type) is what produces its column order of
 * `title, data, favorite, id, type, created_at, modified_at`. Reordering these declarations changes
 * the generated DDL, which changes the identity hash, which makes Room refuse to open real v1
 * files. `LegacySchemaIdentityTest` is the guard.
 *
 * `data` stays a raw `ByteArray` here. v1 decrypted it inside a Room `@TypeConverter`; keeping
 * decryption out of the DAO keeps the crypto injectable and the DAO fakeable.
 */
@Entity(tableName = "SecureElement")
internal data class LegacySecureElementEntity(
    val title: String,
    @ColumnInfo(name = "data") val data: ByteArray,
    val favorite: Boolean = false,
    @Embedded val timestamps: LegacyTimestamps = LegacyTimestamps(),
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
) {
    var type: Int = 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LegacySecureElementEntity) return false

        if (title != other.title) return false
        if (!data.contentEquals(other.data)) return false
        if (favorite != other.favorite) return false
        if (timestamps != other.timestamps) return false
        if (id != other.id) return false
        return type == other.type
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + favorite.hashCode()
        result = 31 * result + timestamps.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + type
        return result
    }
}

internal data class LegacyTimestamps(
    @ColumnInfo(name = "created_at", defaultValue = "CURRENT_TIMESTAMP") val createdAt: Long? = null,
    @ColumnInfo(name = "modified_at") val modifiedAt: Long? = null,
)
