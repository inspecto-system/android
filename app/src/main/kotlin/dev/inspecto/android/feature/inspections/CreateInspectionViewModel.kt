package dev.inspecto.android.feature.inspections

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.inspecto.android.R
import dev.inspecto.android.api.models.ChecklistTemplate
import dev.inspecto.android.api.models.Room
import dev.inspecto.android.core.error.AppError
import dev.inspecto.android.core.error.messageRes
import dev.inspecto.android.feature.properties.PropertyRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject

data class CreateInspectionState(
    val rooms: List<Room> = emptyList(),
    val templates: List<ChecklistTemplate> = emptyList(),
    val selectedRooms: Set<UUID> = emptySet(),
    val selectedTemplates: Set<String> = emptySet(),
    val title: String = "",
    val loading: Boolean = true,
    val submitting: Boolean = false,
    @StringRes val errorRes: Int? = null,
    val createdId: UUID? = null,
)

@HiltViewModel
class CreateInspectionViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val properties: PropertyRepository,
        private val inspections: InspectionRepository,
    ) : ViewModel() {
        private val propertyId: UUID = UUID.fromString(checkNotNull(savedStateHandle.get<String>("propertyId")))
        private val mutableState = MutableStateFlow(CreateInspectionState())

        val state: StateFlow<CreateInspectionState> = mutableState.asStateFlow()

        init {
            reload()
        }

        fun reload() {
            viewModelScope.launch {
                mutableState.update { it.copy(loading = true, errorRes = null) }
                try {
                    val rooms = async { properties.details(propertyId).rooms }
                    val templates = async { inspections.templates() }
                    loaded(rooms.await(), templates.await())
                } catch (e: AppError) {
                    mutableState.update { it.copy(loading = false, errorRes = e.messageRes()) }
                }
            }
        }

        fun toggleRoom(roomId: UUID) = mutableState.update { it.copy(selectedRooms = it.selectedRooms.toggle(roomId)) }

        fun toggleTemplate(code: String) =
            mutableState.update { it.copy(selectedTemplates = it.selectedTemplates.toggle(code)) }

        fun onTitleChange(title: String) = mutableState.update { it.copy(title = title) }

        fun save() {
            val current = state.value
            val error =
                when {
                    current.selectedRooms.isEmpty() -> R.string.inspection_rooms_required
                    current.selectedTemplates.isEmpty() -> R.string.inspection_templates_required
                    else -> null
                }
            if (error != null) {
                mutableState.update { it.copy(errorRes = error) }
                return
            }
            viewModelScope.launch {
                mutableState.update { it.copy(submitting = true, errorRes = null) }
                try {
                    // Rooms in the order of the property card, so the tabs of the inspection follow it.
                    val draft =
                        NewInspection(
                            roomIds = current.rooms.map { it.id }.filter { it in current.selectedRooms },
                            templateCodes =
                                current.templates
                                    .map {
                                        it.code
                                    }.filter { it in current.selectedTemplates },
                            title = current.title,
                            inspectedAt = OffsetDateTime.now(),
                        )
                    val created = inspections.create(propertyId, draft)
                    mutableState.update { it.copy(submitting = false, createdId = created.id) }
                } catch (e: AppError) {
                    mutableState.update { it.copy(submitting = false, errorRes = e.messageRes()) }
                }
            }
        }

        private fun loaded(
            rooms: List<Room>,
            templates: List<ChecklistTemplate>,
        ) {
            val defaultTemplate = templates.firstOrNull { it.code == DEFAULT_TEMPLATE } ?: templates.firstOrNull()
            mutableState.update {
                it.copy(
                    rooms = rooms,
                    templates = templates,
                    selectedRooms = rooms.map { room -> room.id }.toSet(),
                    selectedTemplates = setOfNotNull(defaultTemplate?.code),
                    loading = false,
                    errorRes = if (rooms.isEmpty()) R.string.inspection_no_rooms else null,
                )
            }
        }

        private companion object {
            /** Built-in template for any room (T-104). */
            const val DEFAULT_TEMPLATE = "ROOM_STANDARD"

            fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
        }
    }
