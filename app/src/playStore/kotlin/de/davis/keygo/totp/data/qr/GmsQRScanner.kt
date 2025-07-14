package de.davis.keygo.totp.data.qr

import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import de.davis.keygo.totp.domain.model.camera.Frame
import de.davis.keygo.totp.domain.qr.QRScanner
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.core.annotation.Factory
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Factory
class GmsQRScanner : QRScanner {

    private val scannerOptions = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()

    private val scanner = BarcodeScanning.getClient(scannerOptions)

    override suspend fun scan(frame: Frame): List<String> = suspendCancellableCoroutine { con ->
        val image = InputImage.fromByteArray(
            frame.dataNv21,
            frame.width,
            frame.height,
            frame.rotationDegrees,
            InputImage.IMAGE_FORMAT_NV21
        )

        scanner.process(image).addOnCompleteListener {
            when {
                it.isSuccessful -> {
                    con.resume(it.result.mapNotNull { code -> code.rawValue })
                }

                it.exception != null -> {
                    con.resumeWithException(it.exception!!)
                }

                else -> {
                    con.resume(emptyList())
                }
            }
        }
    }

    override fun close() {
        scanner.close()
    }
}