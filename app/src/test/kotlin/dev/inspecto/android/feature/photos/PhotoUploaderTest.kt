package dev.inspecto.android.feature.photos

import dev.inspecto.android.api.apis.DefectsApi
import dev.inspecto.android.core.db.PendingPhoto
import dev.inspecto.android.core.db.PendingPhotoDao
import dev.inspecto.android.media.api.apis.PhotosApi
import dev.inspecto.android.testing.TestServer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID

/** In-memory queue with the semantics of the Room queries. */
class FakePendingPhotoDao : PendingPhotoDao {
    val rows = MutableStateFlow<Map<String, PendingPhoto>>(emptyMap())

    override suspend fun insert(photo: PendingPhoto) {
        rows.value += photo.id to photo
    }

    override suspend fun ready(): List<PendingPhoto> =
        rows.value.values
            .filter { it.observationId != null && it.errorCode == null }
            .sortedBy { it.createdAt }

    override fun observeInspection(inspectionId: String): Flow<List<PendingPhoto>> =
        rows.map { all -> all.values.filter { it.inspectionId == inspectionId }.sortedBy { it.createdAt } }

    override suspend fun find(id: String): PendingPhoto? = rows.value[id]

    override suspend fun uploaded(
        id: String,
        photoId: String,
    ) = update(id) { it.copy(photoId = photoId) }

    override suspend fun setError(
        id: String,
        errorCode: String?,
    ) = update(id) { it.copy(errorCode = errorCode) }

    override suspend fun delete(id: String) {
        rows.value -= id
    }

    private fun update(
        id: String,
        change: (PendingPhoto) -> PendingPhoto,
    ) {
        rows.value[id]?.let { rows.value += id to change(it) }
    }
}

class PhotoUploaderTest {
    @TempDir
    lateinit var dir: File

    private val server = TestServer()
    private val dao = FakePendingPhotoDao()
    private val uploader =
        PhotoUploader(
            dao,
            server.authorizedRetrofit.create(PhotosApi::class.java),
            server.authorizedRetrofit.create(DefectsApi::class.java),
        )
    private val propertyId = UUID.randomUUID()
    private val observationId = UUID.randomUUID()
    private val photoId = UUID.randomUUID()

    @AfterEach
    fun close() = server.close()

    @Test
    fun shouldUploadWithIdempotencyKeyThenAttachAndForgetThePhoto() =
        runTest {
            val photo = queued("a", createdAt = 1)
            server.enqueue(201, photoJson())
            server.enqueue(201, """{"photoId":"$photoId","sortOrder":0}""")

            assertEquals(QueueResult.DONE, uploader.sendReady())

            val upload = server.server.takeRequest()
            assertEquals("/api/v1/photos", upload.url.encodedPath)
            assertEquals(photo.id, upload.headers["Idempotency-Key"])
            val body = checkNotNull(upload.body).utf8()
            // The form field holds the bare identifier, not a JSON string in quotes.
            val field = body.substringAfter("name=\"propertyId\"").substringBefore("--")
            assertTrue(field.contains("text/plain"), field)
            assertEquals(propertyId.toString(), field.substringAfter("\r\n\r\n").trim(), field)
            assertTrue(body.contains("Content-Type: image/jpeg"))
            val attach = server.server.takeRequest()
            assertEquals("/api/v1/observations/$observationId/photos", attach.url.encodedPath)
            assertEquals("""{"photoId":"$photoId"}""", attach.body?.utf8())
            assertTrue(dao.rows.value.isEmpty())
            assertFalse(File(photo.filePath).exists())
        }

    @Test
    fun shouldOnlyAttachAPhotoUploadedBefore() =
        runTest {
            queued("a", createdAt = 1, uploadedAs = photoId)
            server.enqueue(201, """{"photoId":"$photoId","sortOrder":0}""")

            assertEquals(QueueResult.DONE, uploader.sendReady())

            assertEquals(
                "/api/v1/observations/$observationId/photos",
                server.server
                    .takeRequest()
                    .url.encodedPath,
            )
            assertEquals(1, server.server.requestCount)
        }

    @Test
    fun shouldKeepPhotoAndRetryWhenServerIsTemporarilyFailing() =
        runTest {
            val photo = queued("a", createdAt = 1)
            server.enqueue(503, TestServer.problem(503, "DEPENDENCY_UNAVAILABLE"))

            assertEquals(QueueResult.RETRY, uploader.sendReady())

            assertNull(dao.find(photo.id)?.errorCode)
            assertTrue(File(photo.filePath).exists())
        }

    @Test
    fun shouldRememberUploadWhenAttachIsRetried() =
        runTest {
            val photo = queued("a", createdAt = 1)
            server.enqueue(201, photoJson())
            server.enqueue(503, TestServer.problem(503, "DEPENDENCY_UNAVAILABLE"))

            assertEquals(QueueResult.RETRY, uploader.sendReady())

            assertEquals(photoId.toString(), dao.find(photo.id)?.photoId)
        }

    @Test
    fun shouldSetAsideRejectedPhotoAndContinueWithTheNext() =
        runTest {
            val rejected = queued("a", createdAt = 1, uploadedAs = photoId)
            queued("b", createdAt = 2, uploadedAs = photoId)
            server.enqueue(400, TestServer.problem(400, "PHOTO_LIMIT_EXCEEDED"))
            server.enqueue(201, """{"photoId":"$photoId","sortOrder":1}""")

            assertEquals(QueueResult.DONE, uploader.sendReady())

            assertEquals(
                listOf(rejected.id),
                dao.rows.value.keys
                    .toList(),
            )
            assertEquals("PHOTO_LIMIT_EXCEEDED", dao.find(rejected.id)?.errorCode)
            assertEquals(QueueResult.DONE, uploader.sendReady())
            assertEquals(2, server.server.requestCount)
        }

    @Test
    fun shouldWaitForObservationAndReportMissingFile() =
        runTest {
            queued("local", createdAt = 1).let { dao.rows.value += it.id to it.copy(observationId = null) }
            val lost = queued("lost", createdAt = 2)
            File(lost.filePath).delete()

            assertEquals(QueueResult.DONE, uploader.sendReady())

            assertEquals(PhotoUploader.FILE_MISSING, dao.find(lost.id)?.errorCode)
            assertNull(dao.find("local")?.errorCode)
            assertEquals(0, server.server.requestCount)
        }

    private suspend fun queued(
        id: String,
        createdAt: Long,
        uploadedAs: UUID? = null,
    ): PendingPhoto {
        val file = File(dir, "$id.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val photo =
            PendingPhoto(
                id = id,
                propertyId = propertyId.toString(),
                inspectionId = UUID.randomUUID().toString(),
                observationId = observationId.toString(),
                filePath = file.path,
                photoId = uploadedAs?.toString(),
                createdAt = createdAt,
            )
        dao.insert(photo)
        return photo
    }

    private fun photoJson() =
        """{"id":"$photoId","propertyId":"$propertyId","contentType":"image/jpeg","width":2560,"height":1920,
            "sizeBytes":1000,"createdAt":"2026-11-06T10:00:00Z"}"""
}
