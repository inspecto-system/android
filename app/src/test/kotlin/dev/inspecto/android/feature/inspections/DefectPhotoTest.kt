package dev.inspecto.android.feature.inspections

import androidx.lifecycle.SavedStateHandle
import dev.inspecto.android.api.models.ChecklistItem
import dev.inspecto.android.api.models.CreateDefectRequest
import dev.inspecto.android.api.models.SuggestionItem
import dev.inspecto.android.core.network.NetworkModule
import dev.inspecto.android.feature.drafts.CreateDefect
import dev.inspecto.android.feature.drafts.DraftApplier
import dev.inspecto.android.feature.inspections.InspectionFixtures.cracks
import dev.inspecto.android.feature.inspections.InspectionFixtures.details
import dev.inspecto.android.feature.inspections.InspectionFixtures.inspectionId
import dev.inspecto.android.feature.inspections.InspectionFixtures.item
import dev.inspecto.android.feature.inspections.InspectionFixtures.propertyId
import dev.inspecto.android.feature.photos.PhotoRepository
import dev.inspecto.android.feature.photos.PhotoTarget
import dev.inspecto.android.feature.photos.PreparedPhoto
import dev.inspecto.android.testing.MainDispatcherExtension
import dev.inspecto.android.testing.TestDrafts
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.File
import java.math.BigDecimal
import java.util.UUID

/** Photo step of a new defect with category suggestions (T-603). */
@OptIn(ExperimentalCoroutinesApi::class)
class DefectPhotoTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val photos = mockk<PhotoRepository>(relaxed = true)
    private val suggestions = mockk<SuggestionRepository>()
    private val shot = File("shot.jpg")
    private val prepared = PreparedPhoto("p-1", File("p-1.jpg"))
    private val photoId: UUID = UUID.randomUUID()
    private val suggestionId: UUID = UUID.randomUUID()
    private val crack = SuggestionItem(cracks.id, "CRACK", "Трещина", BigDecimal("0.81"))

    @Test
    fun shouldUploadAtOnceAndShowSuggestions() =
        runTest(main.dispatcher) {
            coEvery { photos.prepare(shot) } returns prepared
            coEvery { photos.uploadNow(prepared, propertyId) } returns photoId
            coEvery { suggestions.suggest(photoId) } returns Suggestions(suggestionId, listOf(crack))
            val viewModel = DefectPhotoViewModel(photos, suggestions)

            viewModel.taken(shot, propertyId)
            advanceUntilIdle()

            val photo = viewModel.state.value.photo
            assertEquals(DefectPhoto(prepared, photoId, Suggestions(suggestionId, listOf(crack))), photo)
            assertFalse(viewModel.state.value.working)
        }

    @Test
    fun shouldKeepThePhotoWithoutNetworkAndAskNothing() =
        runTest(main.dispatcher) {
            coEvery { photos.prepare(shot) } returns prepared
            coEvery { photos.uploadNow(prepared, propertyId) } returns null
            val viewModel = DefectPhotoViewModel(photos, suggestions)

            viewModel.taken(shot, propertyId)
            advanceUntilIdle()

            assertEquals(DefectPhoto(prepared, null, null), viewModel.state.value.photo)
            coVerify(exactly = 0) { suggestions.suggest(any()) }
        }

    @Test
    fun shouldDeleteAReplacedOrCancelledPhoto() =
        runTest(main.dispatcher) {
            val first = PreparedPhoto("p-1", File.createTempFile("p-1", ".jpg"))
            val second = PreparedPhoto("p-2", File.createTempFile("p-2", ".jpg"))
            coEvery { photos.prepare(shot) } returnsMany listOf(first, second)
            coEvery { photos.uploadNow(any(), propertyId) } returns null
            val viewModel = DefectPhotoViewModel(photos, suggestions)

            viewModel.taken(shot, propertyId)
            advanceUntilIdle()
            viewModel.taken(shot, propertyId)
            advanceUntilIdle()
            viewModel.cancel()
            advanceUntilIdle()

            assertFalse(first.file.exists())
            assertFalse(second.file.exists())
            assertNull(viewModel.state.value.photo)
        }

    @Test
    fun shouldDropAPhotoStillUploadingWhenTheDialogIsClosed() =
        runTest(main.dispatcher) {
            val late = PreparedPhoto("p-3", File.createTempFile("p-3", ".jpg"))
            coEvery { photos.prepare(shot) } returns late
            coEvery { photos.uploadNow(late, propertyId) } coAnswers {
                delay(UPLOAD_MILLIS)
                photoId
            }
            val viewModel = DefectPhotoViewModel(photos, suggestions)

            viewModel.taken(shot, propertyId)
            runCurrent()
            viewModel.cancel()
            advanceUntilIdle()

            assertFalse(late.file.exists())
            assertEquals(DefectPhotoState(), viewModel.state.value)
            coVerify(exactly = 0) { suggestions.suggest(any()) }
        }

    @Test
    fun shouldQueueDefectWithSuggestionAndItsPhotoForTheFirstObservation() =
        runTest(main.dispatcher) {
            val walls = item("Стены", result = ChecklistItem.Result.DEFECT)
            val repository = mockk<InspectionRepository>(relaxed = true)
            coEvery { repository.details(inspectionId) } returns details(kitchenItems = listOf(walls))
            val drafts = TestDrafts()
            val viewModel =
                InspectionViewModel(
                    SavedStateHandle(mapOf("inspectionId" to inspectionId.toString())),
                    repository,
                    mockk(relaxed = true),
                    drafts.repository,
                    photos,
                )
            advanceUntilIdle()

            viewModel.showDefectDialog(DefectDraft.forItem(walls))
            val photo = DefectPhoto(prepared, photoId, Suggestions(suggestionId, listOf(crack)))
            viewModel.saveDefect(
                DefectForm(
                    cracks.id,
                    CreateDefectRequest.ElementGroup.WALLS,
                    CreateDefectRequest.Severity.MAJOR,
                    "Трещина",
                    "",
                    photo,
                ),
            )
            advanceUntilIdle()

            val queued =
                drafts.actions.rows.value
                    .single()
            val created = NetworkModule.provideJson().decodeFromString<CreateDefect>(queued.payload)
            assertEquals(suggestionId, created.request.suggestionId)
            val target = PhotoTarget(propertyId, inspectionId, null, DraftApplier.localId(created.key))
            coVerify { photos.enqueue(prepared, target, photoId) }
        }
}

private const val UPLOAD_MILLIS = 1_000L
