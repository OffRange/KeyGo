package de.davis.keygo.totp.data.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import de.davis.keygo.feature.totp.domain.model.camera.Frame
import de.davis.keygo.feature.totp.domain.qr.QRScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single
internal class FdroidQRScanner : QRScanner {

    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }

    override suspend fun scan(frame: Frame): List<String> = withContext(Dispatchers.Default) {
        val source = PlanarYUVLuminanceSource(
            frame.dataNv21,
            frame.width,
            frame.height,
            0,
            0,
            frame.width,
            frame.height,
            false
        )

        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        runCatching {
            val result = reader.decodeWithState(binaryBitmap)
            listOfNotNull(result.text)
        }.getOrDefault(emptyList())
    }

    override fun close() {}
}