package com.example

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.security.configureAppCheck
import com.example.ui.JarvisHudScreen
import com.example.ui.theme.JarvisTheme
import com.example.viewmodel.JarvisViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI or ViewModels
        configureAppCheck(this, intent)

        // STEP 2: Full-bleed edge-to-edge dark HUD presentation
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContent {
            JarvisTheme {
                val jarvisViewModel: JarvisViewModel = viewModel(
                    factory = JarvisViewModel.provideFactory(application)
                )
                JarvisHudScreen(viewModel = jarvisViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureAppCheck(this, intent)
    }
}
