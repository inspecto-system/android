package dev.inspecto.android.feature.photos

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.inspecto.android.R
import dev.inspecto.android.core.db.PendingPhoto
import dev.inspecto.android.core.error.AppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

data class PhotoQueueState(
    val pending: List<PendingPhoto> = emptyList(),
    /** Photos allowed per observation; null until the limits are loaded (the server checks anyway). */
    val maxPerObservation: Int? = null,
    /** Grows when queued photos leave the queue attached: the inspection is then reloaded to show them. */
    val attachedCount: Int = 0,
) {
    fun pendingFor(observationId: UUID): List<PendingPhoto> =
        pending.filter {
            it.observationId ==
                observationId.toString()
        }
}

/** Photo queue of an inspection, shown next to its observations. */
@HiltViewModel
class PhotoQueueViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: PhotoRepository,
    ) : ViewModel() {
        private val inspectionId: UUID = UUID.fromString(checkNotNull(savedStateHandle.get<String>("inspectionId")))
        private val mutableState = MutableStateFlow(PhotoQueueState())

        // Photos the inspector removed: they leave the queue without being attached.
        private val removed = mutableSetOf<String>()

        val state: StateFlow<PhotoQueueState> = mutableState.asStateFlow()

        init {
            viewModelScope.launch {
                repository.pending(inspectionId).collect { list ->
                    mutableState.update { state ->
                        val ids = list.map { it.id }.toSet()
                        val attached = state.pending.count { it.id !in ids && it.id !in removed }
                        state.copy(pending = list, attachedCount = state.attachedCount + attached)
                    }
                }
            }
            viewModelScope.launch {
                try {
                    val limits = repository.limits()
                    mutableState.update { it.copy(maxPerObservation = limits.maxPhotosPerObservation) }
                } catch (ignored: AppError) {
                    // Without the limits the camera stays available; an extra photo fails with PHOTO_LIMIT_EXCEEDED.
                }
            }
        }

        fun retry(photo: PendingPhoto) {
            viewModelScope.launch { repository.retry(photo) }
        }

        fun remove(photo: PendingPhoto) {
            removed += photo.id
            viewModelScope.launch { repository.remove(photo) }
        }

        suspend fun thumbnail(photoId: UUID): Bitmap? = repository.thumbnail(photoId)

        suspend fun preview(photo: PendingPhoto): Bitmap? = repository.preview(photo)
    }

data class CameraState(
    /** Photos the observation already has or that wait in the queue. */
    val count: Int = 0,
    val max: Int? = null,
    val saving: Boolean = false,
    @StringRes val errorRes: Int? = null,
) {
    val full: Boolean get() = max != null && count >= max
}

/** Camera of an observation: every shot goes to the queue; the limit counts attached and queued photos. */
@HiltViewModel
class CameraViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: PhotoRepository,
    ) : ViewModel() {
        private val target =
            PhotoTarget(
                propertyId = UUID.fromString(checkNotNull(savedStateHandle.get<String>("propertyId"))),
                inspectionId = UUID.fromString(checkNotNull(savedStateHandle.get<String>("inspectionId"))),
                observationId = UUID.fromString(checkNotNull(savedStateHandle.get<String>("observationId"))),
            )
        private val attached: Int = savedStateHandle.get<Int>("attached") ?: 0
        private val mutableState = MutableStateFlow(CameraState(count = attached))

        val state: StateFlow<CameraState> = mutableState.asStateFlow()

        init {
            viewModelScope.launch {
                repository.pending(target.inspectionId).collect { list ->
                    val queued = list.count { it.observationId == target.observationId.toString() }
                    mutableState.update { it.copy(count = attached + queued) }
                }
            }
            viewModelScope.launch {
                try {
                    val max = repository.limits().maxPhotosPerObservation
                    mutableState.update { it.copy(max = max) }
                } catch (ignored: AppError) {
                    // The server enforces the limit; see PhotoQueueViewModel.
                }
            }
        }

        /** A shot saved by the camera into a temporary file. */
        fun onShot(file: File) {
            viewModelScope.launch {
                mutableState.update { it.copy(saving = true, errorRes = null) }
                try {
                    repository.enqueue(file, target)
                    mutableState.update { it.copy(saving = false) }
                } catch (ignored: IOException) {
                    mutableState.update { it.copy(saving = false, errorRes = R.string.photo_save_failed) }
                } catch (ignored: IllegalArgumentException) {
                    // Not an image: the camera wrote a broken file.
                    mutableState.update { it.copy(saving = false, errorRes = R.string.photo_save_failed) }
                }
            }
        }

        fun onCaptureFailed() = mutableState.update { it.copy(errorRes = R.string.photo_capture_failed) }
    }
