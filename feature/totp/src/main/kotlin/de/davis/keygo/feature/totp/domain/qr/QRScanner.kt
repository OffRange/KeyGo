package de.davis.keygo.feature.totp.domain.qr

import de.davis.keygo.feature.totp.domain.model.camera.Frame

interface QRScanner {

    suspend fun scan(frame: Frame): List<String>

    fun close()
}