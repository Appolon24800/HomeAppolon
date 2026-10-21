package dev.appolon.homelab

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.appolon.homelab.ui.HomeScreen

class MainActivity : ComponentActivity() {

    private var externalUrl by mutableStateOf<String?>(null)
    private var externalRequestId by mutableStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Hide the status bar (clock, wifi, battery) while the app is in the
        // foreground. Swipe down from the top edge to reveal it temporarily.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        externalUrl = intent?.dataString
        setContent {
            HomeScreen(externalUrl = externalUrl, externalRequestId = externalRequestId)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        externalUrl = intent.dataString
        externalRequestId++
    }
}
