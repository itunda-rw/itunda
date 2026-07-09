package rw.itunda.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import rw.itunda.app.CoreBank.BankActivity

/**
 * Itunda Android Main Entry Point
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Toss routing architecture: Direct user to native Core Bank or Hybrid app based on context
        // We start with Core Bank for high security.
        val intent = Intent(this, BankActivity::class.java)
        startActivity(intent)
        finish()
    }
}
