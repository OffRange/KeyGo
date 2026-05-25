package de.davis.keygo.feature.credit_card

import de.davis.keygo.feature.credit_card.domain.ApduTransport

class FakeApduTransport : ApduTransport {

    var closed: Boolean = false
        private set

    override fun transceive(command: ByteArray): ByteArray = ByteArray(0)

    override fun close() {
        closed = true
    }
}
