package dev.inspecto.android.feature.inspections

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.inspecto.android.api.models.ChecklistItem
import dev.inspecto.android.api.models.CreateDefectRequest
import dev.inspecto.android.api.models.DefectCategory
import dev.inspecto.android.api.models.Inspection
import dev.inspecto.android.api.models.InspectionDetails
import dev.inspecto.android.api.models.Observation
import dev.inspecto.android.api.models.OpenDefect
import dev.inspecto.android.core.db.PendingAction
import dev.inspecto.android.core.error.AppError
import dev.inspecto.android.core.error.messageRes
import dev.inspecto.android.feature.drafts.DraftApplier
import dev.inspecto.android.feature.drafts.DraftRepository
import dev.inspecto.android.feature.drafts.InspectionSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Defect being entered: where it was found; the rest is filled in the dialog. */
data class DefectDraft(
    val roomId: UUID,
    val checklistItemId: UUID?,
    val elementGroup: CreateDefectRequest.ElementGroup,
    val title: String,
) {
    companion object {
        /** Defect found at a checklist item: room, element and title are taken from the item. */
        fun forItem(item: ChecklistItem) =
            DefectDraft(
                roomId = item.roomId,
                checklistItemId = item.id,
                elementGroup = CreateDefectRequest.ElementGroup.valueOf(item.elementGroup.name),
                title = item.title,
            )

        /** Defect outside the checklist of a room. */
        fun forRoom(roomId: UUID) = DefectDraft(roomId, null, CreateDefectRequest.ElementGroup.OTHER, "")
    }
}

/** Fields of the defect dialog. */
data class DefectForm(
    val categoryId: UUID,
    val elementGroup: CreateDefectRequest.ElementGroup,
    val severity: CreateDefectRequest.Severity,
    val title: String,
    val description: String,
)

data class InspectionState(
    val inspection: InspectionDetails? = null,
    val categories: List<DefectCategory> = emptyList(),
    val roomIndex: Int = 0,
    val loading: Boolean = true,
    val busy: Boolean = false,
    @StringRes val errorRes: Int? = null,
    val defectDraft: DefectDraft? = null,
    val openDefects: List<OpenDefect> = emptyList(),
    /** Shown from the snapshot on the device: the server could not be reached. */
    val offline: Boolean = false,
    /** Changes not sent yet (T-308); completion waits until the server has them. */
    val pending: List<PendingAction> = emptyList(),
    val localObservations: Set<UUID> = emptySet(),
) {
    val editable: Boolean get() = inspection?.status == InspectionDetails.Status.IN_PROGRESS

    val failedChanges: Int get() = pending.count { it.errorCode != null }

    /** Defects open since earlier inspections not yet marked as remaining or resolved. */
    val unmarkedOpenDefects: Int get() = openDefects.count { it.currentObservation == null }

    /**
     * Open defects shown on a room tab; defects of rooms outside this inspection are shown on the
     * first tab, so none of them is hidden.
     */
    fun openDefectsIn(roomIndex: Int): List<OpenDefect> {
        val rooms = inspection?.rooms.orEmpty().map { it.room.id }
        val roomId = rooms.getOrNull(roomIndex) ?: return emptyList()
        return openDefects.filter { it.defect.roomId == roomId || (roomIndex == 0 && it.defect.roomId !in rooms) }
    }

    /** Checklist items still without a result, shown before completion. */
    val uncheckedCount: Int
        get() =
            inspection?.rooms.orEmpty().sumOf { room ->
                room.checklist.count { it.result == ChecklistItem.Result.NOT_CHECKED }
            }

    /** Whether a defect was already recorded for a checklist item. */
    fun hasDefect(itemId: UUID): Boolean =
        inspection?.rooms.orEmpty().any { room -> room.observations.any { it.checklistItemId == itemId } }
}

/**
 * Inspection screen, local-first (T-308): every change is saved on the device and shown at once; the
 * offline queue sends it. Without a network the inspection opens from the last snapshot.
 */
@HiltViewModel
class InspectionViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: InspectionRepository,
        private val defects: DefectRepository,
        private val drafts: DraftRepository,
    ) : ViewModel() {
        private val inspectionId: UUID = UUID.fromString(checkNotNull(savedStateHandle.get<String>("inspectionId")))
        private val mutableState = MutableStateFlow(InspectionState())
        private var snapshot: InspectionSnapshot? = null

        val state: StateFlow<InspectionState> = mutableState.asStateFlow()

        init {
            viewModelScope.launch {
                drafts.actions(inspectionId).collect { actions ->
                    val sent = actions.size < state.value.pending.size
                    mutableState.update { it.shown(snapshot, actions, drafts.applier) }
                    // The server now has a change: its answer (ids, versions) replaces the local copy.
                    if (sent) reload()
                }
            }
            reload()
        }

        fun reload() =
            run(showLoading = true) {
                var offline = false
                val fresh =
                    try {
                        coroutineScope {
                            val details = async { repository.details(inspectionId) }
                            val categories = async { defects.categories() }
                            val openDefects = async { defects.openDefects(inspectionId) }
                            InspectionSnapshot(details.await(), openDefects.await(), categories.await())
                        }.also { drafts.saveSnapshot(it) }
                    } catch (e: AppError.Network) {
                        offline = true
                        drafts.snapshot(inspectionId) ?: throw e
                    }
                snapshot = fresh
                mutableState.update { it.shown(fresh, it.pending, drafts.applier).copy(offline = offline) }
            }

        /**
         * Explicit decision of a repeat inspection about a defect found earlier: it remains (OPEN) or
         * is resolved (RESOLVED, ADR-004). A second decision replaces the first one.
         */
        fun markOpenDefect(
            openDefect: OpenDefect,
            status: Observation.Status,
        ) = run { drafts.recordObservation(inspectionId, openDefect.defect.id, status) }

        fun selectRoom(index: Int) = mutableState.update { it.copy(roomIndex = index) }

        fun start() = changeStatus { repository.start(it) }

        fun complete() = changeStatus { repository.complete(it) }

        /** Quick mark of a checklist item; a defect mark opens the defect dialog for that item. */
        fun mark(
            item: ChecklistItem,
            result: ChecklistItem.Result,
        ) = run {
            drafts.setResult(item, result)
            if (result == ChecklistItem.Result.DEFECT && !state.value.hasDefect(item.id)) {
                showDefectDialog(DefectDraft.forItem(item))
            }
        }

        /** Opens the defect dialog for a draft, or closes it for null. */
        fun showDefectDialog(draft: DefectDraft?) = mutableState.update { it.copy(defectDraft = draft) }

        fun saveDefect(form: DefectForm) {
            val draft = state.value.defectDraft ?: return
            run {
                drafts.createDefect(
                    inspectionId,
                    CreateDefectRequest(
                        roomId = draft.roomId,
                        checklistItemId = draft.checklistItemId,
                        categoryId = form.categoryId,
                        elementGroup = form.elementGroup,
                        title = form.title.trim(),
                        severity = form.severity,
                        description = form.description.trim().ifEmpty { null },
                    ),
                )
                mutableState.update { it.copy(defectDraft = null) }
            }
        }

        /** Start and completion need the server: they change what may be edited. */
        private fun changeStatus(action: suspend (InspectionDetails) -> Inspection) {
            val inspection = state.value.inspection ?: return
            run {
                action(inspection)
                reload()
            }
        }

        private fun run(
            showLoading: Boolean = false,
            action: suspend CoroutineScope.() -> Unit,
        ) {
            viewModelScope.launch {
                mutableState.update { it.copy(loading = showLoading || it.loading, busy = true, errorRes = null) }
                try {
                    action()
                } catch (e: AppError) {
                    mutableState.update { it.copy(errorRes = e.messageRes()) }
                } finally {
                    mutableState.update { it.copy(loading = false, busy = false) }
                }
            }
        }

        private companion object {
            /** The snapshot with the unsent changes applied, as the screen shows it. */
            fun InspectionState.shown(
                snapshot: InspectionSnapshot?,
                actions: List<PendingAction>,
                applier: DraftApplier,
            ): InspectionState {
                if (snapshot == null) return copy(pending = actions)
                val view = applier.apply(snapshot, actions)
                return copy(
                    inspection = view.details,
                    openDefects = view.openDefects,
                    categories = snapshot.categories,
                    localObservations = view.localObservations,
                    pending = actions,
                )
            }
        }
    }
