package rw.itunda.app.CoreBank

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import rw.itunda.app.Security.RootDetection

/**
 * Core Banking Native Activity
 * 100% Native Kotlin to ensure peak performance and security, replicating Toss Bank.
 */
class BankActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Verify Security (Zero Trust / FDS)
        if (RootDetection.verifyDeviceIntegrity()) {
            println("Loading highly secure native Core Banking UI")
            setContent {
                MainTabScreen()
            }
        } else {
            // Handle breach
            println("Security breach detected. Core Banking disabled.")
            finish()
        }
    }
}

