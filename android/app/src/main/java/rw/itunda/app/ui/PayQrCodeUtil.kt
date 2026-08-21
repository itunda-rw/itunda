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

/**
 * Real barcode rendering (product-feel correction, item 242) -- this app's own
 * "My code" was QR-only, but KakaoPay's own real screenshots (both their App
 * Store listing and 3 real screenshots of the user's own live app, fetched/sent
 * this session) show the primary payment code is a linear barcode (Code128,
 * Korea's real 바코드결제 standard, works with plain laser POS scanners), with a
 * small QR secondary -- the exact same real correction just made on bank-mfe/iOS.
 * ZXing's `Code128Writer` is already the same real dependency `generatePayQrBitmap`
 * above uses for QR, just a different encoder class -- no new dependency needed.
 */
fun generatePayBarcodeBitmap(content: String, width: Int = 600, height: Int = 160): ImageBitmap {
    val matrix = com.google.zxing.oned.Code128Writer().encode(content, BarcodeFormat.CODE_128, width, height)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    for (x in 0 until width) {
        for (y in 0 until height) {
            bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap.asImageBitmap()
}
