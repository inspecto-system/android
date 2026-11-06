package dev.inspecto.android.feature.inspections

import androidx.annotation.StringRes
import dev.inspecto.android.R
import dev.inspecto.android.api.models.CreateDefectRequest

/**
 * Display names of API enums. Each generated model has its own enum class with the same constant
 * names, so the labels are looked up by name (as in feature/properties/Labels.kt).
 */
@StringRes
fun inspectionStatusLabel(status: String): Int =
    when (status) {
        "DRAFT" -> R.string.inspection_status_draft
        "IN_PROGRESS" -> R.string.inspection_status_in_progress
        else -> R.string.inspection_status_completed
    }

@StringRes
fun elementGroupLabel(group: String): Int =
    when (group) {
        CreateDefectRequest.ElementGroup.WALLS.name -> R.string.element_walls
        CreateDefectRequest.ElementGroup.CEILING.name -> R.string.element_ceiling
        CreateDefectRequest.ElementGroup.FLOOR.name -> R.string.element_floor
        CreateDefectRequest.ElementGroup.WINDOWS.name -> R.string.element_windows
        CreateDefectRequest.ElementGroup.DOORS.name -> R.string.element_doors
        CreateDefectRequest.ElementGroup.PLUMBING.name -> R.string.element_plumbing
        CreateDefectRequest.ElementGroup.ELECTRICAL.name -> R.string.element_electrical
        else -> R.string.element_other
    }

/** Severity scale of ADR-017, from the least to the most serious. */
@StringRes
fun severityLabel(severity: String): Int =
    when (severity) {
        CreateDefectRequest.Severity.COSMETIC.name -> R.string.severity_cosmetic
        CreateDefectRequest.Severity.MINOR.name -> R.string.severity_minor
        CreateDefectRequest.Severity.MAJOR.name -> R.string.severity_major
        else -> R.string.severity_critical
    }
