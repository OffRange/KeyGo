package de.davis.keygo.core.item.data.local.pojo

import androidx.room3.ColumnInfo

/** The columns of a passkey row that identify it, without touching its key material. */
internal data class PasskeyRefPojo(
    @ColumnInfo(name = "credential_id")
    val credentialId: ByteArray,
    val rp: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PasskeyRefPojo) return false

        return credentialId.contentEquals(other.credentialId) && rp == other.rp
    }

    override fun hashCode(): Int = 31 * credentialId.contentHashCode() + rp.hashCode()
}
