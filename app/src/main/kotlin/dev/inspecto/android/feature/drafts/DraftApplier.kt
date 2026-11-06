package dev.inspecto.android.feature.drafts

import dev.inspecto.android.api.models.ChecklistItem
import dev.inspecto.android.api.models.Defect
import dev.inspecto.android.api.models.InspectionDetails
import dev.inspecto.android.api.models.InspectionRoom
import dev.inspecto.android.api.models.Observation
import dev.inspecto.android.api.models.ObservationWithDefect
import dev.inspecto.android.api.models.OpenDefect
import dev.inspecto.android.core.db.PendingAction
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Applies unsent changes to the last state from the server, so the screen shows what the inspector
 * did, with or without a network. The same function rebuilds the view after every refresh, so local
 * changes are never lost when newer server data arrives.
 */
class DraftApplier(
    private val json: Json,
) {
    fun apply(
        snapshot: InspectionSnapshot,
        actions: List<PendingAction>,
    ): DraftView {
        var details = snapshot.details
        var openDefects = snapshot.openDefects
        val local = mutableSetOf<UUID>()
        for (action in actions) {
            when (ActionType.valueOf(action.type)) {
                ActionType.SET_RESULT -> details = details.withResult(json.decodeFromString(action.payload))
                ActionType.CREATE_DEFECT -> {
                    val created = json.decodeFromString<CreateDefect>(action.payload)
                    details = details.withObservation(localDefect(details, created, action.createdAt))
                    local += localId(created.key)
                }
                ActionType.RECORD_OBSERVATION -> {
                    val decision = json.decodeFromString<RecordObservation>(action.payload)
                    val target = openDefects.firstOrNull { it.defect.id == decision.defectId } ?: continue
                    val observation = decided(details, target, decision, action.createdAt)
                    openDefects =
                        openDefects.map { if (it == target) it.copy(currentObservation = observation) else it }
                    details = details.withDecision(target.defect, observation)
                    if (decision.observationId == null) local += observation.id
                }
            }
        }
        return DraftView(details, openDefects, local)
    }

    private fun localDefect(
        details: InspectionDetails,
        created: CreateDefect,
        createdAt: Long,
    ): ObservationWithDefect {
        val request = created.request
        val at = time(createdAt)
        val defect =
            Defect(
                id = UUID.nameUUIDFromBytes(("defect:" + created.key).toByteArray()),
                propertyId = details.propertyId,
                roomId = request.roomId,
                categoryId = request.categoryId,
                elementGroup = Defect.ElementGroup.valueOf(request.elementGroup.name),
                title = request.title,
                severity = Defect.Severity.valueOf(request.severity.name),
                currentStatus = Defect.CurrentStatus.OPEN,
                firstInspectionId = details.id,
                createdAt = at,
                updatedAt = at,
                version = 0,
                description = request.description,
            )
        return ObservationWithDefect(
            id = localId(created.key),
            defectId = defect.id,
            inspectionId = details.id,
            status = ObservationWithDefect.Status.OPEN,
            severity = ObservationWithDefect.Severity.valueOf(request.severity.name),
            observedBy = details.inspectorId,
            photos = emptyList(),
            createdAt = at,
            version = 0,
            defect = defect,
            checklistItemId = request.checklistItemId,
        )
    }

    private fun decided(
        details: InspectionDetails,
        target: OpenDefect,
        decision: RecordObservation,
        createdAt: Long,
    ): Observation {
        val status = Observation.Status.valueOf(decision.status)
        return target.currentObservation?.copy(status = status)
            ?: Observation(
                id = UUID.nameUUIDFromBytes(("observation:${details.id}:${target.defect.id}").toByteArray()),
                defectId = target.defect.id,
                inspectionId = details.id,
                status = status,
                severity = Observation.Severity.valueOf(target.defect.severity.name),
                observedBy = details.inspectorId,
                photos = emptyList(),
                createdAt = time(createdAt),
                version = 0,
            )
    }

    companion object {
        /** Id under which a defect created on the device shows its observation (and photos). */
        fun localId(key: String): UUID = UUID.nameUUIDFromBytes(("observation:$key").toByteArray())

        private fun time(millis: Long): OffsetDateTime = Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC)

        private fun InspectionDetails.withRooms(change: (InspectionRoom) -> InspectionRoom) =
            copy(rooms = rooms.map(change))

        private fun InspectionDetails.withResult(mark: SetResult) =
            withRooms { room ->
                room.copy(
                    checklist =
                        room.checklist.map {
                            if (it.id ==
                                mark.itemId
                            ) {
                                it.copy(result = ChecklistItem.Result.valueOf(mark.result))
                            } else {
                                it
                            }
                        },
                )
            }

        private fun InspectionDetails.withObservation(observation: ObservationWithDefect) =
            withRooms { room ->
                if (room.room.id ==
                    observation.defect.roomId
                ) {
                    room.copy(observations = room.observations + observation)
                } else {
                    room
                }
            }

        /** The decision is shown among the defects of the room, as the server will show it. */
        private fun InspectionDetails.withDecision(
            defect: Defect,
            observation: Observation,
        ) = withRooms { room ->
            if (room.room.id != defect.roomId) return@withRooms room
            val shown =
                ObservationWithDefect(
                    id = observation.id,
                    defectId = defect.id,
                    inspectionId = observation.inspectionId,
                    status = ObservationWithDefect.Status.valueOf(observation.status.name),
                    severity = ObservationWithDefect.Severity.valueOf(observation.severity.name),
                    observedBy = observation.observedBy,
                    photos = observation.photos,
                    createdAt = observation.createdAt,
                    version = observation.version,
                    defect = defect,
                    checklistItemId = observation.checklistItemId,
                    comment = observation.comment,
                )
            val others = room.observations.filterNot { it.defectId == defect.id }
            room.copy(observations = others + shown)
        }
    }
}
