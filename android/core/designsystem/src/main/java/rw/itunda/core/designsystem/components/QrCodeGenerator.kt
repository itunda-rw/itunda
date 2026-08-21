package rw.itunda.core.designsystem.components

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Real, shared QR generation for non-money-critical codes (item 244) --
 * `PayQrCodeUtil.kt`/merchantapp's `QrCodeUtil.kt` deliberately stay separate,
 * per-app duplicates specifically because they render a real payment code
 * (money-critical safety precedent); a chat join code carries no money, so a
 * single shared generator here is the honest, simpler choice instead of a third
 * near-identical copy.
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
