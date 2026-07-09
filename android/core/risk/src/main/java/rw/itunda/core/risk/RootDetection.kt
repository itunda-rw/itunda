package rw.itunda.core.risk

import java.io.File

/**
 * Ported from mobile_clients/android (2026-07-10) into its correct bounded-context
 * module — see ARCHITECTURE.md §3. Device-integrity check used to gate access to
 * native banking/payments screens, mirroring Toss's FDS (fraud detection system)
 * pattern documented in docs/TOSS_ARCHITECTURE_FACTS.md.
 */
object RootDetection {

    fun verifyDeviceIntegrity(): Boolean {
        if (isDeviceRooted()) {
            return false
        }
        return true
    }

    private fun isDeviceRooted(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }
        return false
    }
}
