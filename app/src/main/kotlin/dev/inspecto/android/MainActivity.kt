package dev.inspecto.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import dev.inspecto.android.ui.navigation.InspectoNavHost
import dev.inspecto.android.ui.theme.InspectoTheme

/** Single activity; screens are Compose destinations of [InspectoNavHost]. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            InspectoTheme {
                InspectoNavHost()
            }
        }
    }
}
