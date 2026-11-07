package dev.inspecto.android.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.inspecto.android.core.auth.SessionState
import dev.inspecto.android.feature.auth.LoginScreen
import dev.inspecto.android.feature.auth.RegisterScreen
import dev.inspecto.android.feature.comparison.ComparisonScreen
import dev.inspecto.android.feature.inspections.CreateInspectionScreen
import dev.inspecto.android.feature.inspections.InspectionScreen
import dev.inspecto.android.feature.photos.CameraScreen
import dev.inspecto.android.feature.properties.CreatePropertyScreen
import dev.inspecto.android.feature.properties.PropertyDetailsScreen
import dev.inspecto.android.feature.properties.PropertyListScreen
import dev.inspecto.android.feature.reports.ReportsScreen

/**
 * Root of the UI: sign-in screens while signed out, main screens while signed in. Signing in or
 * out (including an expired session) switches the graph, so back navigation never crosses it.
 */
@Composable
fun InspectoNavHost(sessionViewModel: SessionViewModel = hiltViewModel()) {
    val session by sessionViewModel.state.collectAsStateWithLifecycle()
    when (session) {
        SessionState.UNKNOWN ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        SessionState.SIGNED_OUT -> AuthNavHost()
        SessionState.SIGNED_IN -> MainNavHost()
    }
}

@Composable
private fun AuthNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = LoginRoute) {
        composable<LoginRoute> { LoginScreen(onRegister = { navController.navigate(RegisterRoute) }) }
        composable<RegisterRoute> { RegisterScreen(onLogin = { navController.popBackStack() }) }
    }
}

@Composable
private fun MainNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = PropertyListRoute) {
        composable<PropertyListRoute> {
            PropertyListScreen(
                onCreate = { navController.navigate(CreatePropertyRoute) },
                onOpen = { id -> navController.navigate(PropertyDetailsRoute(id.toString())) },
            )
        }
        composable<CreatePropertyRoute> {
            CreatePropertyScreen(
                onBack = { navController.popBackStack() },
                onCreated = { id ->
                    navController.navigate(PropertyDetailsRoute(id.toString())) {
                        popUpTo(PropertyListRoute)
                    }
                },
            )
        }
        composable<PropertyDetailsRoute> { entry ->
            val propertyId = entry.toRoute<PropertyDetailsRoute>().propertyId
            PropertyDetailsScreen(
                onBack = { navController.popBackStack() },
                onNewInspection = { navController.navigate(CreateInspectionRoute(propertyId)) },
                onOpenInspection = { id -> navController.navigate(InspectionRoute(id.toString())) },
                onCompare = { navController.navigate(CompareRoute(propertyId)) },
            )
        }
        composable<CompareRoute> {
            ComparisonScreen(onBack = { navController.popBackStack() })
        }
        composable<CreateInspectionRoute> {
            CreateInspectionScreen(
                onBack = { navController.popBackStack() },
                onCreated = { id ->
                    navController.navigate(InspectionRoute(id.toString())) {
                        popUpTo<CreateInspectionRoute> { inclusive = true }
                    }
                },
            )
        }
        composable<InspectionRoute> { entry ->
            val inspectionId = entry.toRoute<InspectionRoute>().inspectionId
            InspectionScreen(
                onBack = { navController.popBackStack() },
                onReports = { navController.navigate(ReportsRoute(inspectionId)) },
                onTakePhoto = { photo ->
                    navController.navigate(
                        CameraRoute(
                            photo.propertyId.toString(),
                            inspectionId,
                            photo.observationId.toString(),
                            photo.attached,
                            photo.local,
                        ),
                    )
                },
            )
        }
        composable<ReportsRoute> {
            ReportsScreen(onBack = { navController.popBackStack() })
        }
        composable<CameraRoute> {
            CameraScreen(onBack = { navController.popBackStack() })
        }
    }
}
