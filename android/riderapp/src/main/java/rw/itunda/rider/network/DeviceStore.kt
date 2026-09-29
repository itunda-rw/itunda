package rw.itunda.rider.network

import android.content.Context
import java.util.UUID

/**
 * Real, stable, per-install identifier (item 130) -- this app never had ANY device-id
 * concept before now (unlike merchantapp's own DeviceStore.kt, ported 2026-07-28), so
 * this is new rather than a port. Reused as this demo's client-generated push token
 * (see ApiService.kt's own doc comment on registerDeviceToken) -- this app has no real
 * FCM SDK integrated, and RiderApp's own login has no device-binding/trusted-device
 * concept at all (a rider registers through the consumer app first), so the only real
 * purpose this identifier serves here is a stable push-token stand-in.
 *
 * Plain (unencrypted) SharedPreferences, same as the consumer app's own DeviceStore --
 * not a secret.
 */
class DeviceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("itunda_rider_device", Context.MODE_PRIVATE)

    fun getOrCreateDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (existing != null) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, created).apply()
        return created
    }

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
    }
}
