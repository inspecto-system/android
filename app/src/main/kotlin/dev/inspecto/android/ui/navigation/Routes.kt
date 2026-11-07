package dev.inspecto.android.ui.navigation

import kotlinx.serialization.Serializable

/** Type-safe destinations of the app. */
sealed interface Route

@Serializable
data object LoginRoute : Route

@Serializable
data object RegisterRoute : Route

@Serializable
data object PropertyListRoute : Route

@Serializable
data object CreatePropertyRoute : Route

/** Property card; the identifier is a string because navigation arguments are primitives. */
@Serializable
data class PropertyDetailsRoute(
    val propertyId: String,
) : Route

/** New inspection of a property. */
@Serializable
data class CreateInspectionRoute(
    val propertyId: String,
) : Route

/** Inspection with its checklist and defects. */
@Serializable
data class InspectionRoute(
    val inspectionId: String,
) : Route

/** Comparison of two inspections of a property (T-405). */
@Serializable
data class CompareRoute(
    val propertyId: String,
) : Route

/** PDF reports of a completed inspection (T-404). */
@Serializable
data class ReportsRoute(
    val inspectionId: String,
) : Route

/** Camera of an observation; [attached] photos already on the server count against the limit. */
@Serializable
data class CameraRoute(
    val propertyId: String,
    val inspectionId: String,
    val observationId: String,
    val attached: Int,
    /** The observation exists only on the device (T-308). */
    val local: Boolean = false,
) : Route
