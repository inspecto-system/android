package dev.inspecto.android.feature.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.inspecto.android.BuildConfig
import dev.inspecto.android.core.db.PendingPhoto
import dev.inspecto.android.core.db.PendingPhotoDao
import dev.inspecto.android.core.error.apiCall
import dev.inspecto.android.core.network.AuthorizedApi
import dev.inspecto.android.media.api.apis.PhotosApi
import dev.inspecto.android.media.api.models.PhotoLimits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Where a photo belongs: identifiers on the server (the observation may still be local, T-308). */
data class PhotoTarget(
    val propertyId: UUID,
    val inspectionId: UUID,
    val observationId: UUID?,
)

/** Photos of observations: the local upload queue, limits and thumbnails from media-service. */
@Singleton
class PhotoRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dao: PendingPhotoDao,
        private val photos: PhotosApi,
        private val compressor: PhotoCompressor,
        @AuthorizedApi private val client: OkHttpClient,
    ) {
        private var limits: PhotoLimits? = null
        private val thumbnails = LruCache<UUID, Bitmap>(THUMBNAIL_CACHE)

        /** Upload limits of the server (`GET /photos/limits`), loaded once per app start. */
        suspend fun limits(): PhotoLimits = limits ?: apiCall { photos.getPhotoLimits() }.also { limits = it }

        fun pending(inspectionId: UUID): Flow<List<PendingPhoto>> = dao.observeInspection(inspectionId.toString())

        /** Compresses a camera shot into the queue and deletes the shot; sending starts when possible. */
        suspend fun enqueue(
            shot: File,
            target: PhotoTarget,
        ) {
            val id = UUID.randomUUID().toString()
            val file = File(File(context.filesDir, DIRECTORY), "$id.jpg")
            withContext(Dispatchers.IO) {
                try {
                    compressor.compress(shot, file)
                } finally {
                    shot.delete()
                }
            }
            dao.insert(
                PendingPhoto(
                    id = id,
                    propertyId = target.propertyId.toString(),
                    inspectionId = target.inspectionId.toString(),
                    observationId = target.observationId?.toString(),
                    filePath = file.path,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            PhotoUploadWorker.schedule(WorkManager.getInstance(context))
        }

        /** Sends a photo again after an error (for example, after a photo of the observation was removed). */
        suspend fun retry(photo: PendingPhoto) {
            dao.setError(photo.id, null)
            PhotoUploadWorker.schedule(WorkManager.getInstance(context))
        }

        /** Removes a photo from the queue; an uploaded but unattached copy is deleted by media-service in 24 h. */
        suspend fun remove(photo: PendingPhoto) {
            dao.delete(photo.id)
            withContext(Dispatchers.IO) { File(photo.filePath).delete() }
        }

        /** Small version of an attached photo, or null when it cannot be loaded now. */
        suspend fun thumbnail(photoId: UUID): Bitmap? =
            thumbnails.get(photoId) ?: withContext(Dispatchers.IO) {
                val request =
                    Request
                        .Builder()
                        .url(
                            "${BuildConfig.MEDIA_BASE_URL}photos/$photoId/content?variant=thumb",
                        ).build()
                try {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use null
                        BitmapFactory.decodeStream(response.body.byteStream())
                    }
                } catch (ignored: IOException) {
                    null // shown as an empty frame; the next screen visit tries again
                }
            }?.also { thumbnails.put(photoId, it) }

        /** Local file of a queued photo, decoded small for the list. */
        suspend fun preview(photo: PendingPhoto): Bitmap? =
            withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(
                    photo.filePath,
                    BitmapFactory.Options().apply { inSampleSize = PREVIEW_SAMPLE },
                )
            }

        private companion object {
            const val DIRECTORY = "photos"

            // Thumbnails are ≈320 px (ADR-025): 40 of them take a few megabytes.
            const val THUMBNAIL_CACHE = 40
            const val PREVIEW_SAMPLE = 8
        }
    }
