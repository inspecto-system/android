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
