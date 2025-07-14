package de.davis.keygo.totp.domain.qr

import de.davis.keygo.totp.domain.model.camera.Frame

interface QRScanner {

    suspend fun scan(frame: Frame): List<String>

    fun close()
}