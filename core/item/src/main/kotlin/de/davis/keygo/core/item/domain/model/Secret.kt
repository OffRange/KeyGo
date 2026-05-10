package de.davis.keygo.core.item.domain.model

sealed interface SecretCodec<T> {

    fun encode(value: T): ByteArray
    fun decode(data: ByteArray): T

    object StringCodec : SecretCodec<String> {
        override fun encode(value: String): ByteArray = value.encodeToByteArray()
        override fun decode(data: ByteArray): String = data.decodeToString()
    }

    object ByteArrayCodec : SecretCodec<ByteArray> {
        override fun encode(value: ByteArray): ByteArray = value
        override fun decode(data: ByteArray): ByteArray = data
    }
}

data class EncryptedPayload(
    val ciphertext: ByteArray,
    val iv: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedPayload

        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (!iv.contentEquals(other.iv)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        return result
    }

    companion object {
        val EMPTY
            get() = EncryptedPayload(
                ciphertext = byteArrayOf(),
                iv = byteArrayOf()
            )
    }
}

interface SecretField<T> {
    val payload: EncryptedPayload
    val blueprint: SecretBlueprint<T, out SecretField<T>>
}

abstract class SecretBlueprint<T, F : SecretField<T>> {
    abstract val label: String
    abstract val codec: SecretCodec<T>

    abstract fun createField(payload: EncryptedPayload): F
}
