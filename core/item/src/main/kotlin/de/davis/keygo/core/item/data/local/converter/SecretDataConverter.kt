package de.davis.keygo.core.item.data.local.converter

import androidx.room.TypeConverter
import de.davis.keygo.core.item.domain.model.SecretData
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object SecretDataConverter {

    @TypeConverter
    fun fromSecretDataString(value: SecretData<String>?): ByteArray? = fromSecretData(value)

    @TypeConverter
    fun fromByteArrayToSecretDataString(value: ByteArray?): SecretData<String>? =
        fromByteArray(value)

    private fun <T> fromSecretData(value: SecretData<T>?): ByteArray? = value?.let {
        require(it.iv.size <= Short.MAX_VALUE) { "IV size too large: ${it.iv.size}, max size: ${Short.MAX_VALUE}" }

        val buff = ByteBuffer.allocate(2)
        buff.order(ByteOrder.BIG_ENDIAN)
        buff.putShort(it.iv.size.toShort())
        val size = buff.array()
        byteArrayOf(it.decryptedDataType.uniqueId) + size + it.iv + it.data
    }

    private fun <T> fromByteArray(value: ByteArray?): SecretData<T>? = value?.let {
        if (value.isEmpty()) return null

        @Suppress("UNCHECKED_CAST")
        val type = SecretData.DecryptedDataType
            .getById(value[0]) as? SecretData.DecryptedDataType<T>
            ?: return null

        val ivLengthBytes = value.sliceArray(1..2)
        val buff = ByteBuffer.wrap(ivLengthBytes)
        buff.order(ByteOrder.BIG_ENDIAN)
        val ivSize = buff.short.toInt()

        val iv = value.sliceArray(3 until 3 + ivSize)
        val data = value.sliceArray(3 + ivSize until value.size)
        SecretData(data = data, iv = iv, decryptedDataType = type)
    }
}