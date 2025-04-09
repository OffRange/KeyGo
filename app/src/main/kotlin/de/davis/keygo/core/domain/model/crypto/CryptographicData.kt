package de.davis.keygo.core.domain.model.crypto

@JvmInline
value class CryptographicData(val data: ByteArray) {

    companion object {
        val EMPTY: CryptographicData get() = CryptographicData(byteArrayOf())
    }
}

@OptIn(ExperimentalStdlibApi::class)
fun String.asCryptographicData(): CryptographicData {
    return CryptographicData(hexToByteArray())
}