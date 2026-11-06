package dev.inspecto.android.feature.inspections

import androidx.lifecycle.SavedStateHandle
import dev.inspecto.android.R
import dev.inspecto.android.api.models.ChecklistItem
import dev.inspecto.android.api.models.CreateDefectRequest
import dev.inspecto.android.api.models.Inspection
import dev.inspecto.android.api.models.InspectionDetails
import dev.inspecto.android.api.models.PropertyDetails
import dev.inspecto.android.core.error.AppError
import dev.inspecto.android.core.network.NetworkModule
import dev.inspecto.android.feature.drafts.ActionType
import dev.inspecto.android.feature.drafts.CreateDefect
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
import dev.inspecto.android.testing.TestDrafts
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
import java.io.IOException
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class InspectionViewModelsTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val properties = mockk<PropertyRepository>(relaxed = true)
    private val repository = mockk<InspectionRepository>(relaxed = true)
    private val defects = mockk<DefectRepository>(relaxed = true)
    private val drafts = TestDrafts()

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
            coEvery { repository.details(inspectionId) } returnsMany
                listOf(
                    details(InspectionDetails.Status.DRAFT),
                    details(InspectionDetails.Status.IN_PROGRESS),
                    details(InspectionDetails.Status.COMPLETED).copy(completedAt = now),
                )
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
    fun shouldSaveMarkOnDeviceAndOpenDefectDialogForDefectMark() =
        runTest(main.dispatcher) {
            val walls = item("Стены без трещин")
            coEvery { repository.details(inspectionId) } returns details(kitchenItems = listOf(walls))
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
            // Only the latest mark of an item is sent.
            val queued =
                drafts.actions.rows.value
                    .single()
            assertEquals(ActionType.SET_RESULT.name, queued.type)
            assertTrue(queued.payload.contains("\"result\":\"DEFECT\""))
            assertEquals(ChecklistItem.Result.DEFECT, firstKitchenItem(viewModel).result)
            coVerify(atLeast = 2) { drafts.scheduler.schedule() }
        }

    @Test
    fun shouldNotReopenDialogWhenItemAlreadyHasDefect() =
        runTest(main.dispatcher) {
            val walls = item("Стены без трещин")
            coEvery { repository.details(inspectionId) } returns
                details(kitchenItems = listOf(walls), observations = listOf(observation(walls, "Трещина")))
            val viewModel = inspectionViewModel()
            advanceUntilIdle()

            viewModel.mark(walls, ChecklistItem.Result.DEFECT)
            advanceUntilIdle()

            assertNull(viewModel.state.value.defectDraft)
            assertTrue(viewModel.state.value.hasDefect(walls.id))
        }

    @Test
    fun shouldQueueNewDefectAndShowItAtOnce() =
        runTest(main.dispatcher) {
            val walls = item("Стены без трещин", result = ChecklistItem.Result.DEFECT)
            coEvery { repository.details(inspectionId) } returns details(kitchenItems = listOf(walls))
            val viewModel = inspectionViewModel()
            advanceUntilIdle()

            viewModel.showDefectDialog(DefectDraft.forItem(walls))
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

            val queued =
                drafts.actions.rows.value
                    .single()
            assertEquals(ActionType.CREATE_DEFECT.name, queued.type)
            val created = NetworkModule.provideJson().decodeFromString<CreateDefect>(queued.payload)
            assertEquals(
                CreateDefectRequest(
                    roomId = kitchen.id,
                    checklistItemId = walls.id,
                    categoryId = cracks.id,
                    elementGroup = CreateDefectRequest.ElementGroup.WALLS,
                    title = "Трещина",
                    severity = CreateDefectRequest.Severity.MAJOR,
                ),
                created.request,
            )
            assertNull(viewModel.state.value.defectDraft)
            val shown =
                checkNotNull(viewModel.state.value.inspection)
                    .rooms
                    .first()
                    .observations
                    .single()
            assertEquals("Трещина", shown.defect.title)
            assertTrue(shown.id in viewModel.state.value.localObservations)
            assertTrue(viewModel.state.value.hasDefect(walls.id))
        }

    @Test
    fun shouldOpenFreeDefectForRoomAndDismissIt() =
        runTest(main.dispatcher) {
            coEvery { repository.details(inspectionId) } returns details()
            val viewModel = inspectionViewModel()
            advanceUntilIdle()

            viewModel.selectRoom(1)
            viewModel.showDefectDialog(DefectDraft.forRoom(bedroom.id))
            assertEquals(
                DefectDraft(bedroom.id, null, CreateDefectRequest.ElementGroup.OTHER, ""),
                viewModel.state.value.defectDraft,
            )
            viewModel.showDefectDialog(null)
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
            assertTrue(
                drafts.actions.rows.value
                    .isEmpty(),
            )
        }

    @Test
    fun shouldOpenFromSnapshotWithoutNetworkAndKeepLocalChanges() =
        runTest(main.dispatcher) {
            val walls = item("Стены без трещин")
            coEvery { repository.details(inspectionId) } returns details(kitchenItems = listOf(walls))
            inspectionViewModel().apply {
                advanceUntilIdle()
                mark(walls, ChecklistItem.Result.OK)
                advanceUntilIdle()
            }
            coEvery { repository.details(inspectionId) } throws AppError.Network(IOException())

            // The app was restarted without a network.
            val viewModel = inspectionViewModel()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.offline)
            assertNull(viewModel.state.value.errorRes)
            assertEquals(ChecklistItem.Result.OK, firstKitchenItem(viewModel).result)
            assertEquals(1, viewModel.state.value.pending.size)
        }

    @Test
    fun shouldShowNetworkErrorWithoutSnapshot() =
        runTest(main.dispatcher) {
            coEvery { repository.details(inspectionId) } throws AppError.Network(IOException())
            val viewModel = inspectionViewModel()
            advanceUntilIdle()

            assertNull(viewModel.state.value.inspection)
            assertEquals(R.string.error_network, viewModel.state.value.errorRes)
        }

    @Test
    fun shouldReloadWhenServerReceivedAChange() =
        runTest(main.dispatcher) {
            val walls = item("Стены без трещин")
            coEvery { repository.details(inspectionId) } returns details(kitchenItems = listOf(walls))
            val viewModel = inspectionViewModel()
            advanceUntilIdle()
            viewModel.mark(walls, ChecklistItem.Result.OK)
            advanceUntilIdle()

            coEvery { repository.details(inspectionId) } returns
                details(kitchenItems = listOf(walls.copy(result = ChecklistItem.Result.OK, version = 1)))
            drafts.actions.rows.value = emptyList()
            advanceUntilIdle()

            assertEquals(1L, firstKitchenItem(viewModel).version)
            assertTrue(
                viewModel.state.value.pending
                    .isEmpty(),
            )
            coVerify(exactly = 2) { repository.details(inspectionId) }
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
        InspectionViewModel(
            SavedStateHandle(mapOf("inspectionId" to inspectionId.toString())),
            repository,
            defects,
            drafts.repository,
        )

    private fun firstKitchenItem(viewModel: InspectionViewModel) =
        checkNotNull(viewModel.state.value.inspection)
            .rooms
            .first()
            .checklist
            .first()
}
