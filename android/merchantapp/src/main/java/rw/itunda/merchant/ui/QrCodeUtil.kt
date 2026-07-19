package rw.itunda.merchant.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Real QR code rendering for the register/POS checkout flow -- pure ZXing encoding
 * (no camera/scanning dependency needed here), rendered as a plain Bitmap. A
 * customer's own itunda app scans this and resolves it into a real
 * POST /api/v1/merchant/collect/{intentId} call, the same payload convention
 * merchant-mfe's web POS screen already established via its own `qrcode` npm
 * package.
 */
fun generateQrBitmap(content: String, size: Int = 512): ImageBitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap.asImageBitmap()
}
