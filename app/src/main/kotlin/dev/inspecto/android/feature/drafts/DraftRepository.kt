package dev.inspecto.android.feature.drafts

import dev.inspecto.android.api.models.ChecklistItem
import dev.inspecto.android.api.models.CreateDefectRequest
import dev.inspecto.android.api.models.Observation
import dev.inspecto.android.core.db.CachedJson
import dev.inspecto.android.core.db.CachedJsonDao
import dev.inspecto.android.core.db.PendingAction
import dev.inspecto.android.core.db.PendingActionDao
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Draft of an unfinished inspection on the device (T-308): the last snapshot from the server and
 * the queue of changes not sent yet. Every change is saved here first and sent by [SyncWorker].
 */
@Singleton
class DraftRepository
    @Inject
    constructor(
        private val scheduler: SyncScheduler,
        private val actions: PendingActionDao,
        private val cache: CachedJsonDao,
        val json: Json,
    ) {
        val applier = DraftApplier(json)

        fun actions(inspectionId: UUID): Flow<List<PendingAction>> = actions.observeInspection(inspectionId.toString())

        suspend fun saveSnapshot(snapshot: InspectionSnapshot) =
            cache.put(
                CachedJson(snapshotKey(snapshot.details.id), json.encodeToString(snapshot), System.currentTimeMillis()),
            )

        suspend fun snapshot(inspectionId: UUID): InspectionSnapshot? =
            cache.get(snapshotKey(inspectionId))?.let { json.decodeFromString<InspectionSnapshot>(it.json) }

        suspend fun setResult(
            item: ChecklistItem,
            result: ChecklistItem.Result,
        ) = queue(
            item.inspectionId,
            ActionType.SET_RESULT,
            item.id.toString(),
            json.encodeToString(SetResult(item.id, item.roomId, result.name, item.version)),
        )

        /** Queues a new defect; returns the key that identifies it until the server creates it. */
        suspend fun createDefect(
            inspectionId: UUID,
            request: CreateDefectRequest,
        ): String {
            val key = UUID.randomUUID().toString()
            queue(inspectionId, ActionType.CREATE_DEFECT, key, json.encodeToString(CreateDefect(key, request)))
            return key
        }

        /**
         * Queues a decision about an earlier defect. The observation already on the server is taken
         * from the snapshot, not from the screen, which may show a decision not sent yet.
         */
        suspend fun recordObservation(
            inspectionId: UUID,
            defectId: UUID,
            status: Observation.Status,
        ) {
            val current =
                snapshot(inspectionId)
                    ?.openDefects
                    ?.firstOrNull { it.defect.id == defectId }
                    ?.currentObservation
            val decision = RecordObservation(defectId, status.name, current?.id, current?.version)
            queue(inspectionId, ActionType.RECORD_OBSERVATION, defectId.toString(), json.encodeToString(decision))
        }

        /** Removes changes the server rejected for good, after the inspector confirmed it. */
        suspend fun discardFailed(inspectionId: UUID) = actions.deleteFailed(inspectionId.toString())

        private suspend fun queue(
            inspectionId: UUID,
            type: ActionType,
            targetId: String,
            payload: String,
        ) {
            actions.replace(
                PendingAction(
                    inspectionId = inspectionId.toString(),
                    type = type.name,
                    targetId = targetId,
                    payload = payload,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            scheduler.schedule()
        }

        companion object {
            fun snapshotKey(inspectionId: UUID) = "inspection:$inspectionId"
        }
    }
