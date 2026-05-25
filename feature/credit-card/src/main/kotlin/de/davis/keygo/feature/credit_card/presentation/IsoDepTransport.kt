package de.davis.keygo.feature.credit_card.presentation

import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import de.davis.keygo.feature.credit_card.domain.ApduTransport
import de.davis.keygo.feature.credit_card.domain.exception.CardRemovedException

internal class IsoDepTransport(private val isoDep: IsoDep) : ApduTransport {

    override val historicalBytes: ByteArray
        get() = isoDep.historicalBytes ?: isoDep.hiLayerResponse ?: ByteArray(0)

    override fun transceive(command: ByteArray): ByteArray {
        ensureConnected()
        try {
            return isoDep.transceive(command)
        } catch (e: TagLostException) {
            throw CardRemovedException(e)
        }
    }

    override fun close() {
        runCatching { isoDep.close() }
    }

    private fun ensureConnected() {
        if (isoDep.isConnected) return
        try {
            isoDep.connect()
            isoDep.timeout = CONNECT_TIMEOUT_MS
        } catch (e: Exception) {
            throw CardRemovedException(e)
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
    }
}