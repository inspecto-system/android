package dev.inspecto.android.feature.drafts

import dev.inspecto.android.api.apis.ChecklistApi
import dev.inspecto.android.api.apis.DefectsApi
import dev.inspecto.android.api.apis.InspectionsApi
import dev.inspecto.android.api.models.CreateObservationRequest
import dev.inspecto.android.api.models.Observation
import dev.inspecto.android.api.models.UpdateChecklistItemRequest
import dev.inspecto.android.api.models.UpdateObservationRequest
import dev.inspecto.android.core.db.PendingAction
import dev.inspecto.android.core.db.PendingActionDao
import dev.inspecto.android.core.db.PendingPhotoDao
import dev.inspecto.android.core.error.AppError
import dev.inspecto.android.core.error.apiCall
import dev.inspecto.android.core.error.isTemporary
import dev.inspecto.android.feature.photos.QueueResult
import java.util.UUID
import javax.inject.Inject

/** API operations the queue needs, grouped to keep the sender's dependencies short. */
class DraftApi
    @Inject
    constructor(
        val inspections: InspectionsApi,
        val checklist: ChecklistApi,
        val defects: DefectsApi,
    )

/**
 * Sends queued changes in order (owner's rules for T-308):
 * - `409 VERSION_CONFLICT`: the fresh version is read and the change is sent again with the
 *   inspector's data, which are never lost;
 * - `409 INVALID_STATE_TRANSITION`: the inspection was completed on the server, which wins; the
 *   unsent changes of that inspection are dropped and the inspector is told how many;
 * - a defect created on the device gets its server id here, and its queued photos become ready.
 */
class DraftSender
    @Inject
    constructor(
        private val actions: PendingActionDao,
        private val photos: PendingPhotoDao,
        private val drafts: DraftRepository,
        private val notices: DraftNotices,
        private val api: DraftApi,
    ) {
        suspend fun sendReady(): QueueResult {
            var action = actions.next()
            while (action != null) {
                if (!sendOne(action)) return QueueResult.RETRY
                action = actions.next()
            }
            return QueueResult.DONE
        }

        /** Sends a change and settles its outcome; false when the server cannot be reached now. */
        private suspend fun sendOne(action: PendingAction): Boolean =
            try {
                send(action)
                actions.delete(action.seq)
                true
            } catch (e: AppError.Api) {
                when {
                    e.isTemporary() -> false
                    e.code == INVALID_STATE -> true.also { dropInspection(action) }
                    else -> true.also { actions.setError(action.seq, e.code ?: "HTTP_${e.status}") }
                }
            } catch (e: AppError) {
                if (e.isTemporary()) false else true.also { actions.setError(action.seq, UNREADABLE) }
            }

        private suspend fun send(action: PendingAction) {
            val inspectionId = UUID.fromString(action.inspectionId)
            when (ActionType.valueOf(action.type)) {
                ActionType.SET_RESULT -> setResult(inspectionId, drafts.json.decodeFromString(action.payload))
                ActionType.CREATE_DEFECT -> createDefect(inspectionId, drafts.json.decodeFromString(action.payload))
                ActionType.RECORD_OBSERVATION -> record(inspectionId, drafts.json.decodeFromString(action.payload))
            }
        }

        private suspend fun setResult(
            inspectionId: UUID,
            mark: SetResult,
        ) {
            suspend fun patch(version: Long) =
                apiCall {
                    api.checklist.updateChecklistItem(
                        mark.itemId,
                        UpdateChecklistItemRequest(version, UpdateChecklistItemRequest.Result.valueOf(mark.result)),
                    )
                }
            retryOnConflict({ patch(mark.version) }) {
                val fresh =
                    apiCall { api.inspections.getInspection(inspectionId) }
                        .rooms
                        .flatMap { it.checklist }
                        .first { it.id == mark.itemId }
                patch(fresh.version)
            }
        }

        private suspend fun createDefect(
            inspectionId: UUID,
            created: CreateDefect,
        ) {
            val defect = apiCall { api.defects.createDefect(inspectionId, created.request, created.key) }
            val observation = defect.observations.first { it.inspectionId == inspectionId }
            photos.resolveObservation(DraftApplier.localId(created.key).toString(), observation.id.toString())
        }

        private suspend fun record(
            inspectionId: UUID,
            decision: RecordObservation,
        ) {
            val status = Observation.Status.valueOf(decision.status)
            val known = decision.observationId?.let { it to checkNotNull(decision.version) }
            val observation =
                if (known == null) {
                    retryOnConflict({ create(inspectionId, decision.defectId, status) }) {
                        update(current(inspectionId, decision.defectId), status)
                    }
                } else {
                    retryOnConflict(
                        { update(known, status) },
                    ) { update(current(inspectionId, decision.defectId), status) }
                }
            val localKey = UUID.nameUUIDFromBytes(("observation:$inspectionId:${decision.defectId}").toByteArray())
            photos.resolveObservation(localKey.toString(), observation.id.toString())
        }

        private suspend fun create(
            inspectionId: UUID,
            defectId: UUID,
            status: Observation.Status,
        ): Observation =
            apiCall {
                api.defects.createObservation(
                    inspectionId,
                    CreateObservationRequest(defectId, CreateObservationRequest.Status.valueOf(status.name)),
                )
            }

        private suspend fun update(
            observation: Pair<UUID, Long>,
            status: Observation.Status,
        ): Observation =
            apiCall {
                api.defects.updateObservation(
                    observation.first,
                    UpdateObservationRequest(observation.second, UpdateObservationRequest.Status.valueOf(status.name)),
                )
            }

        /** Observation of this inspection on the server now (after a conflict). */
        private suspend fun current(
            inspectionId: UUID,
            defectId: UUID,
        ): Pair<UUID, Long> {
            val observation =
                apiCall { api.defects.listOpenDefectsForInspection(inspectionId) }
                    .first { it.defect.id == defectId }
                    .currentObservation
            return checkNotNull(observation) { "No observation after a conflict" }.let { it.id to it.version }
        }

        private suspend fun dropInspection(action: PendingAction) {
            val inspectionId = UUID.fromString(action.inspectionId)
            notices.completedElsewhere(inspectionId, actions.deleteInspection(action.inspectionId))
        }

        private companion object {
            const val INVALID_STATE = "INVALID_STATE_TRANSITION"
            const val UNREADABLE = "UNREADABLE_RESPONSE"
            val CONFLICTS = setOf("VERSION_CONFLICT", "ALREADY_OBSERVED")

            /** Runs [first]; after a version conflict or an observation made meanwhile, runs [again]. */
            suspend fun <T> retryOnConflict(
                first: suspend () -> T,
                again: suspend () -> T,
            ): T =
                try {
                    first()
                } catch (e: AppError.Api) {
                    if (e.code !in CONFLICTS) throw e
                    again()
                }
        }
    }
