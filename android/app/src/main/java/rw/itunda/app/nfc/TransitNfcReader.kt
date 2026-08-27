package rw.itunda.app.nfc

import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import rw.itunda.app.ui.LocalRealActivity
import java.nio.charset.StandardCharsets

// Real NFC "collector reads a rider's tapped phone" (2026-08-27, direct user
// follow-up: "for simplification we need nfc") -- the reader-mode counterpart to
// TransitHceService.kt. Only works against an Android rider's phone (running that
// service); an iPhone rider always falls back to the same QR TransitCollectScreen.kt
// already scans with the camera, since iOS can't emulate a card for a third-party app.
//
// AID must match apduservice.xml/TransitHceService.kt's own doc comment exactly:
// 0xF0 (self-assigned, no ISO registration) + ASCII "ITUNDA".
private val TRANSIT_AID = byteArrayOf(0xF0.toByte(), 0x49, 0x54, 0x55, 0x4E, 0x44, 0x41)
private val SELECT_APDU = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, TRANSIT_AID.size.toByte()) + TRANSIT_AID + byteArrayOf(0x00)

/**
 * While composed, listens for an Android rider's NFC tap and reports the code they're
 * presenting. No-ops (calls [onUnavailable] once) if this device has no real NFC
 * hardware -- the caller's own camera-scan fallback (CameraQrScanner, already shared
 * via core/designsystem) covers that device and any iPhone rider either way.
 */
@Composable
fun TransitNfcListener(onCodeRead: (String) -> Unit, onUnavailable: () -> Unit) {
    val activity = LocalRealActivity.current
    val onCodeReadState = rememberUpdatedState(onCodeRead)
    val nfcAdapter = remember { NfcAdapter.getDefaultAdapter(activity) }

    DisposableEffect(Unit) {
        if (nfcAdapter == null) {
            onUnavailable()
            return@DisposableEffect onDispose {}
        }
        val callback = NfcAdapter.ReaderCallback { tag ->
            val isoDep = IsoDep.get(tag) ?: return@ReaderCallback
            try {
                isoDep.connect()
                isoDep.timeout = 3000
                val response = isoDep.transceive(SELECT_APDU)
                val statusOk = response.size >= 2 &&
                    response[response.size - 2] == 0x90.toByte() && response[response.size - 1] == 0x00.toByte()
                if (statusOk) {
                    val code = String(response.copyOfRange(0, response.size - 2), StandardCharsets.UTF_8)
                    activity.runOnUiThread { onCodeReadState.value(code) }
                }
                // A non-OK status means the tapped phone isn't currently presenting a
                // real code (TransitHceService's own STATUS_NOT_FOUND) -- the rider
                // just needs to open their "My payment code" screen first and tap
                // again; no error surfaced here, same as a QR scan finding nothing yet.
            } catch (_: Exception) {
                // Real, non-critical -- the tag moved away mid-exchange or the phone
                // isn't running itunda at all. The rider just taps again.
            } finally {
                try { isoDep.close() } catch (_: Exception) {}
            }
        }
        nfcAdapter.enableReaderMode(
            activity, callback,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null,
        )
        onDispose { nfcAdapter.disableReaderMode(activity) }
    }
}
