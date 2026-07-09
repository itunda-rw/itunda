package rw.itunda.app.Security

import java.io.File

/**
 * Itunda - Security Layer - Root Detection & FDS
 * Analyzes device integrity to prevent Fraud and unauthorized access, similar to Toss FDS.
 */
object RootDetection {

    fun verifyDeviceIntegrity(): Boolean {
        if (isDeviceRooted()) {
            println("SECURITY ALERT: Device is rooted. Denying Core Banking Access.")
            // In a production environment for Rwanda, we might allow limited access to 
            // USSD-based features but block high-value app-based transactions.
            return false
        }
        return true
    }

    private fun isDeviceRooted(): Boolean {
        // Basic root detection mock
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
