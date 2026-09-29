package rw.itunda.app.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import rw.itunda.core.designsystem.components.TransitPresentmentStore
import java.nio.charset.StandardCharsets

// Real NFC "tap to present my payment code" (2026-08-27, direct user follow-up: "for
// simplification we need nfc"). Android is the only platform that can do this side of
// the exchange -- third-party card emulation is Apple-restricted on iOS (only Apple
// Pay/Wallet gets Secure Element access), so an iPhone rider always falls back to
// showing the same code as a QR (see TransitCollectScreen.kt's own doc comment).
//
// AID is self-assigned under the 0xF0 "proprietary, no ISO registration required"
// RID prefix (ISO 7816-5) -- itunda has no real ISO-registered AID, same honest
// "not a real X" disclosure this feature draws everywhere else. Only meaningful to
// itunda's own reader (TransitNfcReader.kt) selecting this exact AID; a real Kigali
// Tap&Go reader would never select it and would simply see no compatible card.
//
// Registered in AndroidManifest.xml + res/xml/apduservice.xml. The response to a
// SELECT AID command is the current code from TransitPresentmentStore, UTF-8 encoded
// -- the same string already shown as a QR, just delivered over a different real
// transport. No code currently presented (or it expired) responds with SW=6A82 (file
// not found), the standard ISO 7816-4 way to say "nothing here."
class TransitHceService : HostApduService() {
    companion object {
        // The SELECT AID APDU header every NFC reader sends first: CLA=00 INS=A4
        // (SELECT) P1=04 (select by name) P2=00.
        private val SELECT_APDU_HEADER = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00)
        private val STATUS_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val STATUS_NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null || commandApdu.size < SELECT_APDU_HEADER.size ||
            !commandApdu.copyOfRange(0, SELECT_APDU_HEADER.size).contentEquals(SELECT_APDU_HEADER)
        ) {
            return STATUS_NOT_FOUND
        }
        val code = TransitPresentmentStore.currentCode() ?: return STATUS_NOT_FOUND
        return code.toByteArray(StandardCharsets.UTF_8) + STATUS_OK
    }

    override fun onDeactivated(reason: Int) {
        // Real, non-critical -- the reader either got the code (and the collector's
        // own screen moves on) or the tap was interrupted, in which case the rider
        // just taps again. No state to clean up here specifically.
    }
}
