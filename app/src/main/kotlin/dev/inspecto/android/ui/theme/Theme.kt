package dev.inspecto.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Navy = Color(0xFF1F4E79)
private val NavyLight = Color(0xFF9CC3EA)
private val Amber = Color(0xFFB26A00)
private val AmberLight = Color(0xFFFFB95C)

private val LightColors =
    lightColorScheme(
        primary = Navy,
        secondary = Amber,
    )

private val DarkColors =
    darkColorScheme(
        primary = NavyLight,
        secondary = AmberLight,
    )

/** Material 3 theme of the app with light and dark colour schemes. */
@Composable
fun InspectoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
