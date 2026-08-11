package rw.itunda.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Real QR rendering for the customer-presented payment code (2026-08-11) -- pure
 * ZXing encoding, no camera/scanning dependency needed here (this app only ever
 * DISPLAYS its own code, never scans one). Same exact pattern as merchantapp's own
 * QrCodeUtil.kt (generateQrBitmap), duplicated rather than shared across modules
 * since :app and :merchantapp are separate Gradle applications with no shared
 * "ui utils" module between them.
 */
fun generatePayQrBitmap(content: String, size: Int = 512): ImageBitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap.asImageBitmap()
}
