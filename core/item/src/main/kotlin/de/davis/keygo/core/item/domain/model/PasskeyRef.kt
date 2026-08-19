package de.davis.keygo.core.item.domain.model

/**
 * A login's passkey, as much of it as anything outside the passkey table needs to know.
 *
 * The credential id is what identifies a passkey, not [rp]. One login can hold two credentials for
 * the same relying party, for example two accounts on the same site, so listing or deleting them by
 * relying party alone silently treats the two as one.
 *
 * Carries no key material. It is a handle onto a row, not the row itself.
 */
class PasskeyRef(
    val credentialId: ByteArray,
    val rp: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PasskeyRef) return false

        return credentialId.contentEquals(other.credentialId) && rp == other.rp
    }

    override fun hashCode(): Int = 31 * credentialId.contentHashCode() + rp.hashCode()

    override fun toString(): String = "PasskeyRef(rp=$rp)"
}
