package rw.itunda.merchant.network

import android.content.Context
import android.os.Build
import java.util.UUID

/**
 * Real device binding client-side counterpart (2026-07-28 port) -- see the backend's
 * TrustedDevice.kt/DeviceService.kt doc comments for the full account (modeled on
 * Toss's own real, published Gateway/Passport architecture). Ported from the main
 * app's own rw.itunda.core.network.DeviceStore, which merchantapp doesn't depend on
 * (a separate app, its own separate session, matching TokenStore's own doc comment).
 *
 * Plain (unencrypted) SharedPreferences, unlike TokenStore -- a deviceId is not a
 * secret; it's sent openly on every login already, and the backend never trusts it
 * alone (see DeviceVerificationFilter).
 */
class DeviceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("itunda_merchant_device", Context.MODE_PRIVATE)

    fun getOrCreateDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (existing != null) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, created).apply()
        return created
    }

    // A real, honest, minimal device label -- not a precise fingerprint, matching the
    // main app's own DeviceStore.getDeviceName() in spirit.
    fun getDeviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
    }
}
