package dev.inspecto.android.feature.properties

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.inspecto.android.api.models.CreateRoomRequest
import dev.inspecto.android.api.models.PropertyDetails
import dev.inspecto.android.api.models.Room
import dev.inspecto.android.core.error.AppError
import dev.inspecto.android.core.error.messageRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class PropertyDetailsState(
    val property: PropertyDetails? = null,
    val loading: Boolean = true,
    @StringRes val errorRes: Int? = null,
)

@HiltViewModel
class PropertyDetailsViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: PropertyRepository,
    ) : ViewModel() {
        // Type-safe routes store their properties under the property names (PropertyDetailsRoute.propertyId).
        private val propertyId: UUID = UUID.fromString(checkNotNull(savedStateHandle.get<String>("propertyId")))
        private val mutableState = MutableStateFlow(PropertyDetailsState())

        val state: StateFlow<PropertyDetailsState> = mutableState.asStateFlow()

        init {
            reload()
        }

        fun reload() =
            run(showLoading = true) {
                mutableState.update { it.copy(property = repository.details(propertyId)) }
            }

        fun addRoom(
            name: String,
            type: CreateRoomRequest.RoomType,
        ) = run {
            repository.addRoom(propertyId, name, type)
            reloadRooms()
        }

        fun renameRoom(
            room: Room,
            name: String,
        ) = run {
            repository.renameRoom(room, name)
            reloadRooms()
        }

        fun deleteRoom(room: Room) =
            run {
                repository.deleteRoom(room.id)
                reloadRooms()
            }

        private suspend fun reloadRooms() {
            mutableState.update { it.copy(property = repository.details(propertyId)) }
        }

        private fun run(
            showLoading: Boolean = false,
            action: suspend () -> Unit,
        ) {
            viewModelScope.launch {
                mutableState.update { it.copy(loading = showLoading || it.loading, errorRes = null) }
                try {
                    action()
                    mutableState.update { it.copy(loading = false) }
                } catch (e: AppError) {
                    mutableState.update { it.copy(loading = false, errorRes = e.messageRes()) }
                }
            }
        }
    }
