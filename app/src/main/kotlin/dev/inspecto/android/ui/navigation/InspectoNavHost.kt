package dev.inspecto.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.inspecto.android.feature.start.StartScreen

/** Navigation graph of the app; destinations are type-safe routes. */
@Composable
fun InspectoNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = StartRoute) {
        composable<StartRoute> { StartScreen() }
    }
}
