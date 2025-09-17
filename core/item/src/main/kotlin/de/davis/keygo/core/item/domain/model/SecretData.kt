package de.davis.keygo.core.item.domain.model

data class SecretData<T>(
    val data: ByteArray,
    val decryptedDataType: DecryptedDataType<T>
) {

    sealed interface DecryptedDataType<T> {
        val uniqueId: Byte

        fun decode(data: ByteArray): T
        fun encode(value: T): ByteArray

        object StringType : DecryptedDataType<String> {
            override val uniqueId: Byte = 0x1
            override fun decode(data: ByteArray): String = data.decodeToString()
            override fun encode(value: String): ByteArray = value.encodeToByteArray()
        }

        companion object {
            fun getById(id: Byte): DecryptedDataType<*>? = when (id) {
                StringType.uniqueId -> StringType
                else -> null
            }

            inline fun <reified T> getDecryptedDataType(): DecryptedDataType<T> = when (T::class) {
                String::class -> StringType
                else -> throw IllegalArgumentException("Unsupported type: ${T::class}")
            } as DecryptedDataType<T>
        }
    }

    companion object {
        val EMPTY_STRING: SecretData<String>
            get() = SecretData(
                byteArrayOf(),
                DecryptedDataType.StringType
            )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SecretData<*>

        if (!data.contentEquals(other.data)) return false
        if (decryptedDataType != other.decryptedDataType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + decryptedDataType.hashCode()
        return result
    }
}
