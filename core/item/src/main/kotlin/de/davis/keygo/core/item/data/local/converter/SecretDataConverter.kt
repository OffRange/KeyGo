package de.davis.keygo.core.item.data.local.converter

import androidx.room.TypeConverter
import de.davis.keygo.core.item.domain.model.SecretData

internal object SecretDataConverter {

    @TypeConverter
    fun fromSecretDataString(value: SecretData<String>?): ByteArray? = fromSecretData(value)

    @TypeConverter
    fun fromByteArrayToSecretDataString(value: ByteArray?): SecretData<String>? =
        fromByteArray(value)

    private fun <T> fromSecretData(value: SecretData<T>?): ByteArray? = value?.let {
        byteArrayOf(it.decryptedDataType.uniqueId) + it.data
    }

    private fun <T> fromByteArray(value: ByteArray?): SecretData<T>? = value?.let {
        if (value.isEmpty()) return null

        @Suppress("UNCHECKED_CAST")
        val type = SecretData.DecryptedDataType
            .getById(value[0]) as? SecretData.DecryptedDataType<T>
            ?: return null

        val data = value.sliceArray(1 until value.size)
        SecretData(data = data, decryptedDataType = type)
    }
}