package de.davis.keygo.core.data.converter.crypto

import androidx.room.TypeConverter
import de.davis.keygo.core.domain.model.crypto.CryptographicData

object CryptoConverter {

    @TypeConverter
    fun fromCryptographicDataToByteArray(value: CryptographicData?): ByteArray? = value?.data

    @TypeConverter
    fun fromByteArrayToCryptographicData(value: ByteArray?): CryptographicData? =
        value?.let { CryptographicData(it) }
}