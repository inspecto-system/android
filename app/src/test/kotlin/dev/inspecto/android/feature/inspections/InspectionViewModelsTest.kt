package dev.inspecto.android.feature.inspections

import androidx.lifecycle.SavedStateHandle
import dev.inspecto.android.R
import dev.inspecto.android.api.models.ChecklistItem
import dev.inspecto.android.api.models.CreateDefectRequest
import dev.inspecto.android.api.models.Inspection
import dev.inspecto.android.api.models.InspectionDetails
import dev.inspecto.android.api.models.PropertyDetails
import dev.inspecto.android.core.error.AppError
import dev.inspecto.android.feature.inspections.InspectionFixtures.bedroom
import dev.inspecto.android.feature.inspections.InspectionFixtures.cracks
import dev.inspecto.android.feature.inspections.InspectionFixtures.details
import dev.inspecto.android.feature.inspections.InspectionFixtures.doorCategory
import dev.inspecto.android.feature.inspections.InspectionFixtures.inspection
import dev.inspecto.android.feature.inspections.InspectionFixtures.inspectionId
import dev.inspecto.android.feature.inspections.InspectionFixtures.item
import dev.inspecto.android.feature.inspections.InspectionFixtures.kitchen
import dev.inspecto.android.feature.inspections.InspectionFixtures.now
import dev.inspecto.android.feature.inspections.InspectionFixtures.observation
import dev.inspecto.android.feature.inspections.InspectionFixtures.propertyId
import dev.inspecto.android.feature.inspections.InspectionFixtures.standard
import dev.inspecto.android.feature.inspections.InspectionFixtures.wet
import dev.inspecto.android.feature.properties.PropertyRepository
import dev.inspecto.android.testing.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class InspectionViewModelsTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val properties = mockk<PropertyRepository>(relaxed = true)
    private val repository = mockk<InspectionRepository>(relaxed = true)

    @Test
    fun shouldPreselectAllRoomsAndStandardTemplate() =
        runTest(main.dispatcher) {
            givenProperty()
            val viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(setOf(kitchen.id, bedroom.id), state.selectedRooms)
            assertEquals(setOf("ROOM_STANDARD"), state.selectedTemplates)
            assertFalse(state.loading)
            assertNull(state.errorRes)
        }

    @Test
    fun shouldRequireRoomAndTemplate() =
        runTest(main.dispatcher) {
            givenProperty()
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.toggleRoom(kitchen.id)
            viewModel.toggleRoom(bedroom.id)
            viewModel.save()
            assertEquals(R.string.inspection_rooms_required, viewModel.state.value.errorRes)

            viewModel.toggleRoom(kitchen.id)
            viewModel.toggleTemplate("ROOM_STANDARD")
            viewModel.save()
            advanceUntilIdle()
            assertEquals(R.string.inspection_templates_required, viewModel.state.value.errorRes)
            coVerify(exactly = 0) { repository.create(any(), any()) }
        }

    @Test
    fun shouldCreateInspectionWithRoomsInCardOrder() =
        runTest(main.dispatcher) {
            givenProperty()
            val draft = slot<NewInspection>()
            coEvery { repository.create(propertyId, capture(draft)) } returns details(InspectionDetails.Status.DRAFT)
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.toggleTemplate("WET_ROOM")
            viewModel.toggleRoom(kitchen.id)
            viewModel.toggleRoom(kitchen.id)
            viewModel.onTitleChange("Приёмка")
            viewModel.save()
            advanceUntilIdle()

            assertEquals(listOf(kitchen.id, bedroom.id), draft.captured.roomIds)
            assertEquals(listOf("ROOM_STANDARD", "WET_ROOM"), draft.captured.templateCodes)
            assertEquals("Приёмка", draft.captured.title)
            assertEquals(inspectionId, viewModel.state.value.createdId)
        }

    @Test
    fun shouldExplainPropertyWithoutRooms() =
        runTest(main.dispatcher) {
            coEvery { properties.details(propertyId) } returns property().copy(rooms = emptyList())
            coEvery { repository.templates() } returns listOf(wet)
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(R.string.inspection_no_rooms, viewModel.state.value.errorRes)
            assertEquals(setOf("WET_ROOM"), viewModel.state.value.selectedTemplates)
        }

    @Test
    fun shouldStartAndCompleteInspection() =
        runTest(main.dispatcher) {
            coEvery { repository.details(inspectionId) } returns details(InspectionDetails.Status.DRAFT)
            coEvery { repository.start(any()) } returns inspection(Inspection.Status.IN_PROGRESS)
            coEvery { repository.complete(any()) } returns inspection(Inspection.Status.COMPLETED)
            val viewModel = inspectionViewModel()
            advanceUntilIdle()
            assertFalse(viewModel.state.value.editable)

            viewModel.start()
            advanceUntilIdle()
            assertTrue(viewModel.state.value.editable)
            assertEquals(2, viewModel.state.value.uncheckedCount)

            viewModel.complete()
            advanceUntilIdle()
            val completed = viewModel.state.value.inspection
            assertEquals(InspectionDetails.Status.COMPLETED, completed?.status)
            assertEquals(now, completed?.completedAt)
            assertFalse(viewModel.state.value.editable)
        }

    @Test
    fun shouldShowErrorWhenInspectionWasCompletedElsewhere() =
        runTest(main.dispatcher) {
            coEvery { repository.details(inspectionId) } returns details()
            coEvery { repository.complete(any()) } throws AppError.Api(409, "INVALID_STATE_TRANSITION")
            val viewModel = inspectionViewModel()
            advanceUntilIdle()

            viewModel.complete()
            advanceUntilIdle()

            assertEquals(R.string.error_invalid_state, viewModel.state.value.errorRes)
            assertFalse(viewModel.state.value.busy)
        }

    @Test
    fun shouldMarkItemAndOpenDefectDialogForDefectMark() =
        runTest(main.dispatcher) {
            val walls = item("Стены без трещин")
            coEvery { repository.details(inspectionId) } returns details(kitchenItems = listOf(walls))
            coEvery { repository.setResult(walls, any()) } answers {
                walls.copy(result = secondArg(), version = 1)
            }
            val viewModel = inspectionViewModel()
            advanceUntilIdle()

            viewModel.mark(walls, ChecklistItem.Result.OK)
            advanceUntilIdle()
            assertEquals(ChecklistItem.Result.OK, firstKitchenItem(viewModel).result)
            assertNull(viewModel.state.value.defectDraft)

            viewModel.mark(walls, ChecklistItem.Result.DEFECT)
            advanceUntilIdle()
            val draft = checkNotNull(viewModel.state.value.defectDraft)
            assertEquals(DefectDraft(kitchen.id, walls.id, CreateDefectRequest.ElementGroup.WALLS, walls.title), draft)
            assertEquals(1L, firstKitchenItem(viewModel).version)
        }

    @Test
    fun shouldNotReopenDialogWhenItemAlreadyHasDefect() =
        runTest(main.dispatcher) {
            val walls = item("Стены без трещин")
            coEvery { repository.details(inspectionId) } returns
                details(kitchenItems = listOf(walls), observations = listOf(observation(walls, "Трещина")))
            coEvery { repository.setResult(walls, any()) } returns walls.copy(result = ChecklistItem.Result.DEFECT)
            val viewModel = inspectionViewModel()
            advanceUntilIdle()

            viewModel.mark(walls, ChecklistItem.Result.DEFECT)
            advanceUntilIdle()

            assertNull(viewModel.state.value.defectDraft)
            assertTrue(viewModel.state.value.hasDefect(walls.id))
        }

    @Test
    fun shouldCreateDefectAndReloadInspection() =
        runTest(main.dispatcher) {
            val walls = item("Стены без трещин", result = ChecklistItem.Result.DEFECT)
            coEvery { repository.details(inspectionId) } returnsMany
                listOf(
                    details(kitchenItems = listOf(walls)),
                    details(kitchenItems = listOf(walls), observations = listOf(observation(walls, "Трещина"))),
                )
            val request = slot<CreateDefectRequest>()
            coEvery { repository.createDefect(inspectionId, capture(request)) } returns mockk()
            val viewModel = inspectionViewModel()
            advanceUntilIdle()

            viewModel.openDefect(DefectDraft.forItem(walls))
            viewModel.saveDefect(
                DefectForm(
                    categoryId = cracks.id,
                    elementGroup = CreateDefectRequest.ElementGroup.WALLS,
                    severity = CreateDefectRequest.Severity.MAJOR,
                    title = " Трещина ",
                    description = " ",
                ),
            )
            advanceUntilIdle()

            assertEquals(
                CreateDefectRequest(
                    roomId = kitchen.id,
                    checklistItemId = walls.id,
                    categoryId = cracks.id,
                    elementGroup = CreateDefectRequest.ElementGroup.WALLS,
                    title = "Трещина",
                    severity = CreateDefectRequest.Severity.MAJOR,
                ),
                request.captured,
            )
            assertNull(viewModel.state.value.defectDraft)
            assertEquals(
                1,
                viewModel.state.value.inspection
                    ?.rooms
                    ?.first()
                    ?.observations
                    ?.size,
            )
        }

    @Test
    fun shouldOpenFreeDefectForRoomAndDismissIt() =
        runTest(main.dispatcher) {
            coEvery { repository.details(inspectionId) } returns details()
            val viewModel = inspectionViewModel()
            advanceUntilIdle()

            viewModel.selectRoom(1)
            viewModel.openDefect(DefectDraft.forRoom(bedroom.id))
            assertEquals(
                DefectDraft(bedroom.id, null, CreateDefectRequest.ElementGroup.OTHER, ""),
                viewModel.state.value.defectDraft,
            )
            viewModel.dismissDefect()
            viewModel.saveDefect(
                DefectForm(
                    cracks.id,
                    CreateDefectRequest.ElementGroup.OTHER,
                    CreateDefectRequest.Severity.MINOR,
                    "x",
                    "",
                ),
            )
            advanceUntilIdle()

            assertNull(viewModel.state.value.defectDraft)
            assertEquals(1, viewModel.state.value.roomIndex)
            coVerify(exactly = 0) { repository.createDefect(any(), any()) }
        }

    @Test
    fun shouldListCategoriesOfElementGroupFirst() {
        val inactive = cracks.copy(id = UUID.randomUUID(), active = false)
        assertEquals(
            listOf(doorCategory, cracks),
            categoriesFor(listOf(cracks, inactive, doorCategory), CreateDefectRequest.ElementGroup.DOORS),
        )
    }

    private fun givenProperty() {
        coEvery { properties.details(propertyId) } returns property()
        coEvery { repository.templates() } returns listOf(standard, wet)
    }

    private fun property() =
        PropertyDetails(
            id = propertyId,
            name = "Квартира",
            type = PropertyDetails.Type.APARTMENT,
            address = "ул. Ленина, 1",
            createdAt = now,
            updatedAt = now,
            version = 0,
            rooms = listOf(kitchen, bedroom),
            inspectionCount = 0,
            openDefectCount = 0,
        )

    private fun createViewModel() =
        CreateInspectionViewModel(
            SavedStateHandle(mapOf("propertyId" to propertyId.toString())),
            properties,
            repository,
        )

    private fun inspectionViewModel() =
        InspectionViewModel(SavedStateHandle(mapOf("inspectionId" to inspectionId.toString())), repository)

    private fun firstKitchenItem(viewModel: InspectionViewModel) =
        checkNotNull(viewModel.state.value.inspection)
            .rooms
            .first()
            .checklist
            .first()
}
