package rw.itunda.app.network

import android.content.Context
import android.os.Build
import java.util.UUID

/**
 * Real device binding client-side counterpart (2026-07-21) -- see the backend's
 * TrustedDevice.kt / DeviceService.kt doc comments for the full account (modeled on
 * Toss's own real, published Gateway/Passport architecture). Ported from bank-mfe's
 * lib/device.ts, which shipped this first (2026-07-20, web-only): closes the
 * "Android and iOS haven't been ported yet" gap docs/TOSS_PARITY_MATRIX.md's Device
 * Security row named as its own honest follow-up. Same real, stable, per-install
 * identifier persisted locally -- never a derived hardware fingerprint, this app has
 * no interest in silently fingerprinting a device, only in recognizing "the same
 * phone that logged in before."
 *
 * Plain (unencrypted) SharedPreferences, unlike TokenStore -- a deviceId is not a
 * secret; it's sent openly on every login/register request already, and the backend
 * never trusts it alone (see DeviceVerificationFilter).
 */
class DeviceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("itunda_device", Context.MODE_PRIVATE)

    fun getOrCreateDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (existing != null) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, created).apply()
        return created
    }

    // A real, honest, minimal device label -- enough for a user to recognize "oh,
    // that's my phone" on their own Devices screen, matching bank-mfe's
    // getDeviceName() browser/OS label in spirit (not a precise fingerprint).
    fun getDeviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
    }
}
