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
 * `title, data, favorite, id, type, created_at, modified_at`.
 *
 * What that order does and does not buy is worth being precise about, because getting it wrong in
 * either direction is expensive. Room derives its identity hash from sorted fields and validates an
 * opened file by comparing columns by name, so column order is runtime-inert: reordering these
 * declarations would neither change the hash nor stop a real v1 file from opening. What the hash
 * does pin is column names, affinities, nullability, defaults, the primary key, indices and foreign
 * keys, and drifting on any of those is what makes Room refuse the file. Column order is kept
 * identical to v1 purely for byte-fidelity, so the schema we ship is the schema v1 shipped, and
 * `LegacySchemaIdentityTest` guards both properties with separate assertions.
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
