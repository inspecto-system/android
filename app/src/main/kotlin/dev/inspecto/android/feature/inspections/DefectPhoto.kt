package dev.inspecto.android.feature.inspections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.inspecto.android.api.apis.SuggestionsApi
import dev.inspecto.android.api.models.SuggestionItem
import dev.inspecto.android.api.models.SuggestionRequest
import dev.inspecto.android.core.error.AppError
import dev.inspecto.android.core.error.apiCall
import dev.inspecto.android.feature.photos.PhotoRepository
import dev.inspecto.android.feature.photos.PreparedPhoto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

/** Categories ml-service suggests for an uploaded photo (T-602); suggestions are optional. */
class SuggestionRepository
    @Inject
    constructor(
        private val api: SuggestionsApi,
    ) {
        /** The answer, or null when there is none (no network, server error). */
        suspend fun suggest(photoId: UUID): Suggestions? =
            try {
                val answer = apiCall { api.suggestCategories(SuggestionRequest(photoId)) }
                Suggestions(answer.suggestionId, answer.items)
            } catch (ignored: AppError) {
                // Suggestions are optional: no answer means no suggestion, never an error.
                null
            }
    }

/**
 * Suggestions shown for a photo.
 *
 * @param suggestionId to send with the new defect; null when there are no suggestions
 */
data class Suggestions(
    val suggestionId: UUID?,
    val items: List<SuggestionItem>,
)

/** The photo taken in the new-defect dialog and what the model suggested for it. */
data class DefectPhoto(
    val photo: PreparedPhoto,
    /** Set when the photo was uploaded at once; otherwise the queue uploads it later. */
    val photoId: UUID? = null,
    val suggestions: Suggestions? = null,
)

data class DefectPhotoState(
    val photo: DefectPhoto? = null,
    val working: Boolean = false,
)

/**
 * Photo step of a new defect (T-603): the shot is compressed, uploaded at once when there is a
 * network and ml-service is asked for categories. Without a network the photo simply waits in the
 * queue and there are no suggestions.
 */
@HiltViewModel
class DefectPhotoViewModel
    @Inject
    constructor(
        private val photos: PhotoRepository,
        private val suggestions: SuggestionRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(DefectPhotoState())
        val state: StateFlow<DefectPhotoState> = mutableState.asStateFlow()
        private var taking: Job? = null

        /** A new shot replaces the previous one (also one still being processed). */
        fun taken(
            shot: File,
            propertyId: UUID,
        ) {
            stopTaking()
            state.value.photo
                ?.photo
                ?.discard()
            mutableState.update { DefectPhotoState(working = true) }
            taking =
                viewModelScope.launch {
                    val prepared = photos.prepare(shot)
                    try {
                        val photoId = photos.uploadNow(prepared, propertyId)
                        val answer = photoId?.let { suggestions.suggest(it) }
                        mutableState.update { DefectPhotoState(DefectPhoto(prepared, photoId, answer)) }
                    } catch (e: CancellationException) {
                        // The dialog was closed or another shot taken meanwhile: this photo is not used.
                        prepared.discard()
                        throw e
                    }
                }
        }

        /** The dialog was closed: a photo that was not saved with a defect is deleted. */
        fun cancel() {
            stopTaking()
            state.value.photo
                ?.photo
                ?.discard()
            mutableState.update { DefectPhotoState() }
        }

        /** The defect was saved and took the photo into the queue. */
        fun saved() = mutableState.update { DefectPhotoState() }

        private fun stopTaking() {
            taking?.cancel()
            taking = null
        }
    }
