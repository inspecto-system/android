package dev.inspecto.android.feature.drafts

import dev.inspecto.android.api.models.CreateDefectRequest
import dev.inspecto.android.api.models.DefectCategory
import dev.inspecto.android.api.models.InspectionDetails
import dev.inspecto.android.api.models.OpenDefect
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.UUID

/** Kinds of changes the offline queue sends (`pending_action.type`). */
enum class ActionType {
    /** Result of a checklist item: `PATCH /checklist-items/{id}`. */
    SET_RESULT,

    /** New defect with its first observation: `POST /inspections/{id}/defects` with `Idempotency-Key`. */
    CREATE_DEFECT,

    /** "Remains / resolved" decision about an earlier defect: create or change the observation. */
    RECORD_OBSERVATION,
}

/** Checklist mark; [version] is the one the inspector saw, refreshed on `409 VERSION_CONFLICT`. */
@Serializable
data class SetResult(
    @Contextual val itemId: UUID,
    @Contextual val roomId: UUID,
    val result: String,
    val version: Long,
)

/** New defect; [key] identifies it on the device and is its `Idempotency-Key` on the server. */
@Serializable
data class CreateDefect(
    val key: String,
    val request: CreateDefectRequest,
)

/**
 * Decision about an earlier defect; [observationId] and [version] describe the observation of this
 * inspection already on the server, if there was one when the inspector decided.
 */
@Serializable
data class RecordObservation(
    @Contextual val defectId: UUID,
    val status: String,
    @Contextual val observationId: UUID? = null,
    val version: Long? = null,
)

/** Last state of an inspection received from the server, kept for working without a network. */
@Serializable
data class InspectionSnapshot(
    val details: InspectionDetails,
    val openDefects: List<OpenDefect>,
    val categories: List<DefectCategory>,
)

/** Inspection as the inspector sees it: the snapshot with the unsent changes applied. */
data class DraftView(
    val details: InspectionDetails,
    val openDefects: List<OpenDefect>,
    /** Observations that exist only on the device; their photos wait until the server creates them. */
    val localObservations: Set<UUID>,
)
