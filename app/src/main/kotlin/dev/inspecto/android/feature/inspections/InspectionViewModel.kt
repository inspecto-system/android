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
import dev.inspecto.android.core.error.AppError
import dev.inspecto.android.core.error.messageRes
import kotlinx.coroutines.async
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
) {
    val editable: Boolean get() = inspection?.status == InspectionDetails.Status.IN_PROGRESS

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

@HiltViewModel
class InspectionViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: InspectionRepository,
        private val defects: DefectRepository,
    ) : ViewModel() {
        private val inspectionId: UUID = UUID.fromString(checkNotNull(savedStateHandle.get<String>("inspectionId")))
        private val mutableState = MutableStateFlow(InspectionState())

        val state: StateFlow<InspectionState> = mutableState.asStateFlow()

        init {
            reload()
        }

        fun reload() =
            run(showLoading = true) {
                val details = async { repository.details(inspectionId) }
                val categories = async { defects.categories() }
                val openDefects = async { defects.openDefects(inspectionId) }
                mutableState.update {
                    it.copy(
                        inspection = details.await(),
                        categories = categories.await(),
                        openDefects = openDefects.await(),
                    )
                }
            }

        /**
         * Explicit decision of a repeat inspection about a defect found earlier: it remains (OPEN) or
         * is resolved (RESOLVED, ADR-004). A second decision changes the observation of this inspection.
         */
        fun markOpenDefect(
            openDefect: OpenDefect,
            status: Observation.Status,
        ) = run {
            defects.recordObservation(inspectionId, openDefect, status)
            val details = async { repository.details(inspectionId) }
            val openDefects = async { defects.openDefects(inspectionId) }
            mutableState.update { it.copy(inspection = details.await(), openDefects = openDefects.await()) }
        }

        fun selectRoom(index: Int) = mutableState.update { it.copy(roomIndex = index) }

        fun start() = changeStatus { repository.start(it) }

        fun complete() = changeStatus { repository.complete(it) }

        /** Quick mark of a checklist item; a defect mark opens the defect dialog for that item. */
        fun mark(
            item: ChecklistItem,
            result: ChecklistItem.Result,
        ) = run {
            val updated = repository.setResult(item, result)
            mutableState.update { it.copy(inspection = it.inspection?.withItem(updated)) }
            if (result == ChecklistItem.Result.DEFECT && !state.value.hasDefect(item.id)) {
                showDefectDialog(DefectDraft.forItem(item))
            }
        }

        /** Opens the defect dialog for a draft, or closes it for null. */
        fun showDefectDialog(draft: DefectDraft?) = mutableState.update { it.copy(defectDraft = draft) }

        fun saveDefect(form: DefectForm) {
            val draft = state.value.defectDraft ?: return
            run {
                defects.createDefect(
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
                mutableState.update { it.copy(defectDraft = null, inspection = repository.details(inspectionId)) }
            }
        }

        private fun changeStatus(action: suspend (InspectionDetails) -> Inspection) {
            val inspection = state.value.inspection ?: return
            run {
                val changed = action(inspection)
                mutableState.update {
                    it.copy(
                        inspection =
                            it.inspection?.copy(
                                status = InspectionDetails.Status.valueOf(changed.status.name),
                                version = changed.version,
                                completedAt = changed.completedAt,
                            ),
                    )
                }
            }
        }

        private fun run(
            showLoading: Boolean = false,
            action: suspend kotlinx.coroutines.CoroutineScope.() -> Unit,
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
            fun InspectionDetails.withItem(item: ChecklistItem): InspectionDetails =
                copy(
                    rooms =
                        rooms.map { room ->
                            if (room.room.id == item.roomId) {
                                room.copy(checklist = room.checklist.map { if (it.id == item.id) item else it })
                            } else {
                                room
                            }
                        },
                )
        }
    }
