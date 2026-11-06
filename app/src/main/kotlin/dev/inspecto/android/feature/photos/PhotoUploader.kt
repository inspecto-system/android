package dev.inspecto.android.feature.photos

import dev.inspecto.android.api.apis.DefectsApi
import dev.inspecto.android.api.models.AttachPhotoRequest
import dev.inspecto.android.core.db.PendingPhoto
import dev.inspecto.android.core.db.PendingPhotoDao
import dev.inspecto.android.core.error.AppError
import dev.inspecto.android.core.error.apiCall
import dev.inspecto.android.core.error.isTemporary
import dev.inspecto.android.media.api.apis.PhotosApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.UUID
import javax.inject.Inject

/** Result of one pass over the queue. */
enum class QueueResult {
    /** Every ready photo was attached or set aside with an error the inspector has to resolve. */
    DONE,

    /** The server cannot be reached or is temporarily failing: the pass is repeated later. */
    RETRY,
}

/**
 * Sends queued photos: upload to media-service with the photo's `Idempotency-Key`, then attach to
 * the observation in core-service (repeating an attachment returns it again). A photo whose
 * observation has no server id yet is not ready and waits (T-308).
 */
class PhotoUploader
    @Inject
    constructor(
        private val dao: PendingPhotoDao,
        private val photos: PhotosApi,
        private val defects: DefectsApi,
    ) {
        suspend fun sendReady(): QueueResult {
            for (photo in dao.ready()) {
                try {
                    send(photo)
                } catch (e: AppError) {
                    if (e.isTemporary()) return QueueResult.RETRY
                    dao.setError(photo.id, (e as? AppError.Api)?.let { it.code ?: "HTTP_${it.status}" } ?: UNREADABLE)
                }
            }
            return QueueResult.DONE
        }

        private suspend fun send(photo: PendingPhoto) {
            val file = File(photo.filePath)
            val photoId =
                photo.photoId ?: run {
                    if (!file.exists()) {
                        dao.setError(photo.id, FILE_MISSING)
                        return
                    }
                    val part = MultipartBody.Part.createFormData("file", file.name, file.asRequestBody(JPEG))
                    val uploaded =
                        apiCall { photos.uploadPhoto(part, UUID.fromString(photo.propertyId), photo.id) }.id.toString()
                    dao.uploaded(photo.id, uploaded)
                    uploaded
                }
            apiCall {
                defects.attachPhoto(
                    UUID.fromString(checkNotNull(photo.observationId)),
                    AttachPhotoRequest(UUID.fromString(photoId)),
                )
            }
            dao.delete(photo.id)
            file.delete()
        }

        companion object {
            /** The compressed file disappeared (for example, app data was cleared). */
            const val FILE_MISSING = "FILE_MISSING"

            /** The server answered with a body the app cannot read. */
            const val UNREADABLE = "UNREADABLE_RESPONSE"
            private val JPEG = "image/jpeg".toMediaType()
        }
    }
