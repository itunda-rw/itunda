package com.itunda.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.itunda.app.navigation.ItundaNavGraph

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Edge-to-edge + transparent system bars ────────────────────────
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.statusBarColor     = Color.Transparent.toArgb()
        window.navigationBarColor = Color.White.toArgb()

        // Light icons on white/light backgrounds (Toss is always light theme)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars     = true
            isAppearanceLightNavigationBars = true
        }

        setContent {
            ItundaNavGraph()
        }
    }
}
