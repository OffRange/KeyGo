package de.davis.keygo.core.domain.model.crypto

data class CryptographicData(val data: ByteArray) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CryptographicData

        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        return data.contentHashCode()
    }

    companion object {
        val EMPTY: CryptographicData get() = CryptographicData(byteArrayOf())
    }
}

@OptIn(ExperimentalStdlibApi::class)
fun String.asCryptographicData(): CryptographicData {
    return CryptographicData(hexToByteArray())
}