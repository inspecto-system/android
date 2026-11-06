package dev.inspecto.android.feature.inspections

import androidx.lifecycle.SavedStateHandle
import dev.inspecto.android.api.models.Observation
import dev.inspecto.android.api.models.Room
import dev.inspecto.android.core.network.NetworkModule
import dev.inspecto.android.feature.drafts.ActionType
import dev.inspecto.android.feature.drafts.RecordObservation
import dev.inspecto.android.feature.inspections.InspectionFixtures.bedroom
import dev.inspecto.android.feature.inspections.InspectionFixtures.details
import dev.inspecto.android.feature.inspections.InspectionFixtures.inspectionId
import dev.inspecto.android.feature.inspections.InspectionFixtures.kitchen
import dev.inspecto.android.feature.inspections.InspectionFixtures.propertyId
import dev.inspecto.android.testing.MainDispatcherExtension
import dev.inspecto.android.testing.TestDrafts
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.UUID

/** Repeat inspection: explicit "remains / resolved" decisions about defects of earlier inspections (T-403). */
@OptIn(ExperimentalCoroutinesApi::class)
class RepeatInspectionTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val repository = mockk<InspectionRepository>(relaxed = true)
    private val defects = mockk<DefectRepository>(relaxed = true)
    private val drafts = TestDrafts()

    @Test
    fun shouldGroupOpenDefectsByRoomAndCountUnmarked() =
        runTest(main.dispatcher) {
            val hall = Room(UUID.randomUUID(), propertyId, "Прихожая", Room.RoomType.HALLWAY, 2, 0)
            val kitchenCrack = openDefect("Трещина", kitchen)
            val bedroomLeak = openDefect("Протечка", bedroom, current = Observation.Status.RESOLVED)
            val hallDoor = openDefect("Дверь", hall)
            coEvery { repository.details(inspectionId) } returns details()
            coEvery { defects.openDefects(inspectionId) } returns listOf(kitchenCrack, bedroomLeak, hallDoor)
            val viewModel = viewModel()
            advanceUntilIdle()

            val state = viewModel.state.value
            // The hall is not part of this inspection: its defect is shown on the first tab.
            assertEquals(listOf(kitchenCrack, hallDoor), state.openDefectsIn(0))
            assertEquals(listOf(bedroomLeak), state.openDefectsIn(1))
            assertEquals(emptyList<Any>(), state.openDefectsIn(5))
            assertEquals(2, state.unmarkedOpenDefects)
        }

    @Test
    fun shouldQueueDecisionAndShowItAtOnce() =
        runTest(main.dispatcher) {
            val crack = openDefect("Трещина")
            coEvery { repository.details(inspectionId) } returns details()
            coEvery { defects.openDefects(inspectionId) } returns listOf(crack)
            val viewModel = viewModel()
            advanceUntilIdle()
            assertEquals(1, viewModel.state.value.unmarkedOpenDefects)

            viewModel.markOpenDefect(crack, Observation.Status.RESOLVED)
            advanceUntilIdle()

            assertEquals(0, viewModel.state.value.unmarkedOpenDefects)
            val shown =
                viewModel.state.value.openDefects
                    .single()
                    .currentObservation
            assertEquals(Observation.Status.RESOLVED, shown?.status)
            assertTrue(checkNotNull(shown).id in viewModel.state.value.localObservations)
            val kitchenDefects = checkNotNull(viewModel.state.value.inspection).rooms.first().observations
            assertEquals(listOf(crack.defect.id), kitchenDefects.map { it.defectId })
            val queued =
                drafts.actions.rows.value
                    .single()
            assertEquals(ActionType.RECORD_OBSERVATION.name, queued.type)
            assertTrue(queued.payload.contains("\"status\":\"RESOLVED\""))
        }

    @Test
    fun shouldChangeDecisionOfObservationAlreadyOnServer() =
        runTest(main.dispatcher) {
            val marked = openDefect("Трещина", current = Observation.Status.OPEN)
            coEvery { repository.details(inspectionId) } returns details()
            coEvery { defects.openDefects(inspectionId) } returns listOf(marked)
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.markOpenDefect(marked, Observation.Status.RESOLVED)
            advanceUntilIdle()

            val decision =
                NetworkModule.provideJson().decodeFromString<RecordObservation>(
                    drafts.actions.rows.value
                        .single()
                        .payload,
                )
            assertEquals(RecordObservation(marked.defect.id, "RESOLVED", marked.currentObservation?.id, 2), decision)
            assertTrue(
                viewModel.state.value.localObservations
                    .isEmpty(),
            )
        }

    private fun viewModel() =
        InspectionViewModel(
            SavedStateHandle(mapOf("inspectionId" to inspectionId.toString())),
            repository,
            defects,
            drafts.repository,
        )
}
