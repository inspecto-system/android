package dev.inspecto.android.feature.photos

import androidx.lifecycle.SavedStateHandle
import dev.inspecto.android.R
import dev.inspecto.android.core.db.PendingPhoto
import dev.inspecto.android.core.error.AppError
import dev.inspecto.android.media.api.models.PhotoLimits
import dev.inspecto.android.testing.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.File
import java.io.IOException
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoViewModelsTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val repository = mockk<PhotoRepository>(relaxed = true)
    private val propertyId = UUID.randomUUID()
    private val inspectionId = UUID.randomUUID()
    private val observationId = UUID.randomUUID()
    private val queue = MutableStateFlow<List<PendingPhoto>>(emptyList())
    private val limits =
        PhotoLimits(listOf(PhotoLimits.AllowedContentTypes.IMAGE_SLASH_JPEG), 15_728_640, 50_000_000, 10)

    @Test
    fun shouldShowQueueOfInspectionByObservation() =
        runTest(main.dispatcher) {
            every { repository.pending(inspectionId) } returns queue
            coEvery { repository.limits() } returns limits
            val viewModel = PhotoQueueViewModel(handle(), repository)
            queue.value = listOf(pending("a", observationId), pending("b", UUID.randomUUID()))
            advanceUntilIdle()

            assertEquals(10, viewModel.state.value.maxPerObservation)
            assertEquals(
                listOf("a"),
                viewModel.state.value
                    .pendingFor(observationId)
                    .map { it.id },
            )

            viewModel.retry(queue.value[0])
            viewModel.remove(queue.value[1])
            advanceUntilIdle()
            coVerify { repository.retry(queue.value[0]) }
            coVerify { repository.remove(queue.value[1]) }
        }

    @Test
    fun shouldCountAttachedButNotRemovedPhotos() =
        runTest(main.dispatcher) {
            every { repository.pending(inspectionId) } returns queue
            val viewModel = PhotoQueueViewModel(handle(), repository)
            val first = pending("a", observationId)
            val second = pending("b", observationId)
            queue.value = listOf(first, second)
            advanceUntilIdle()

            viewModel.remove(second)
            queue.value = listOf(first)
            advanceUntilIdle()
            assertEquals(0, viewModel.state.value.attachedCount)

            queue.value = emptyList()
            advanceUntilIdle()
            assertEquals(1, viewModel.state.value.attachedCount)
        }

    @Test
    fun shouldKeepCameraAvailableWithoutLimits() =
        runTest(main.dispatcher) {
            every { repository.pending(inspectionId) } returns queue
            coEvery { repository.limits() } throws AppError.Network(IOException())
            val viewModel = CameraViewModel(handle(attached = 9), repository)
            advanceUntilIdle()

            assertNull(viewModel.state.value.max)
            assertFalse(viewModel.state.value.full)
        }

    @Test
    fun shouldCountAttachedAndQueuedAgainstLimit() =
        runTest(main.dispatcher) {
            every { repository.pending(inspectionId) } returns queue
            coEvery { repository.limits() } returns limits
            val viewModel = CameraViewModel(handle(attached = 9), repository)
            advanceUntilIdle()
            assertFalse(viewModel.state.value.full)

            queue.value = listOf(pending("a", observationId), pending("b", UUID.randomUUID()))
            advanceUntilIdle()

            assertEquals(10, viewModel.state.value.count)
            assertTrue(viewModel.state.value.full)
        }

    @Test
    fun shouldQueueShotAndReportFailures() =
        runTest(main.dispatcher) {
            every { repository.pending(inspectionId) } returns queue
            val shot = File("shot.jpg")
            coEvery { repository.enqueue(shot, any()) } throws IllegalArgumentException("Not an image")
            val viewModel = CameraViewModel(handle(), repository)

            viewModel.onShot(shot)
            advanceUntilIdle()
            assertEquals(R.string.photo_save_failed, viewModel.state.value.errorRes)
            coVerify { repository.enqueue(shot, PhotoTarget(propertyId, inspectionId, observationId)) }

            viewModel.onCaptureFailed()
            assertEquals(R.string.photo_capture_failed, viewModel.state.value.errorRes)
        }

    @Test
    fun shouldExplainQueueErrors() {
        assertEquals(R.string.photo_error_limit, photoErrorMessage("PHOTO_LIMIT_EXCEEDED"))
        assertEquals(R.string.photo_error_completed, photoErrorMessage("INVALID_STATE_TRANSITION"))
        assertEquals(R.string.photo_error_too_large, photoErrorMessage("IMAGE_TOO_LARGE"))
        assertEquals(R.string.photo_error_missing, photoErrorMessage(PhotoUploader.FILE_MISSING))
        assertEquals(R.string.photo_error_other, photoErrorMessage("HTTP_404"))
    }

    private fun handle(attached: Int = 0) =
        SavedStateHandle(
            mapOf(
                "propertyId" to propertyId.toString(),
                "inspectionId" to inspectionId.toString(),
                "observationId" to observationId.toString(),
                "attached" to attached,
            ),
        )

    private fun pending(
        id: String,
        observation: UUID,
    ) = PendingPhoto(
        id,
        propertyId.toString(),
        inspectionId.toString(),
        observation.toString(),
        "$id.jpg",
        createdAt = 1,
    )
}
