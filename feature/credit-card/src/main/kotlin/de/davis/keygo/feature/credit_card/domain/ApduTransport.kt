package de.davis.keygo.feature.credit_card.domain

interface ApduTransport : AutoCloseable {

    val historicalBytes: ByteArray get() = ByteArray(0)
    fun transceive(command: ByteArray): ByteArray
}