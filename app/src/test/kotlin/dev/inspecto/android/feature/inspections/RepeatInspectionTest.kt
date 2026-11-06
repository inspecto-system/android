package dev.inspecto.android.feature.inspections

import androidx.lifecycle.SavedStateHandle
import dev.inspecto.android.api.apis.DefectsApi
import dev.inspecto.android.api.apis.DictionariesApi
import dev.inspecto.android.api.models.Observation
import dev.inspecto.android.api.models.Room
import dev.inspecto.android.feature.inspections.InspectionFixtures.bedroom
import dev.inspecto.android.feature.inspections.InspectionFixtures.details
import dev.inspecto.android.feature.inspections.InspectionFixtures.inspectionId
import dev.inspecto.android.feature.inspections.InspectionFixtures.kitchen
import dev.inspecto.android.feature.inspections.InspectionFixtures.propertyId
import dev.inspecto.android.testing.MainDispatcherExtension
import dev.inspecto.android.testing.TestServer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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
    fun shouldRecordDecisionAndReloadInspection() =
        runTest(main.dispatcher) {
            val crack = openDefect("Трещина")
            val marked = openDefect("Трещина", current = Observation.Status.RESOLVED)
            coEvery { repository.details(inspectionId) } returns details()
            coEvery { defects.openDefects(inspectionId) } returnsMany listOf(listOf(crack), listOf(marked))
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.markOpenDefect(crack, Observation.Status.RESOLVED)
            advanceUntilIdle()

            coVerify { defects.recordObservation(inspectionId, crack, Observation.Status.RESOLVED) }
            coVerify(exactly = 2) { repository.details(inspectionId) }
            assertEquals(0, viewModel.state.value.unmarkedOpenDefects)
        }

    @Test
    fun shouldCreateObservationForFirstDecisionAndUpdateItAfterwards() =
        runTest {
            val server = TestServer()
            try {
                val retrofit = server.authorizedRetrofit
                val wire =
                    DefectRepository(
                        retrofit.create(DefectsApi::class.java),
                        retrofit.create(DictionariesApi::class.java),
                    )
                val first = openDefect("Трещина")
                val again = openDefect("Трещина", current = Observation.Status.OPEN)
                val answer = observationJson(first.defect.id)
                server.enqueue(201, answer)
                server.enqueue(200, answer)

                wire.recordObservation(inspectionId, first, Observation.Status.OPEN)
                wire.recordObservation(inspectionId, again, Observation.Status.RESOLVED)

                val create = server.server.takeRequest()
                assertEquals("/api/v1/inspections/$inspectionId/observations", create.url.encodedPath)
                assertEquals("""{"defectId":"${first.defect.id}","status":"OPEN"}""", create.body?.utf8())
                val update = server.server.takeRequest()
                assertEquals("PATCH", update.method)
                assertEquals("/api/v1/observations/${again.currentObservation?.id}", update.url.encodedPath)
                assertEquals("""{"version":2,"status":"RESOLVED"}""", update.body?.utf8())
            } finally {
                server.close()
            }
        }

    private fun viewModel() =
        InspectionViewModel(SavedStateHandle(mapOf("inspectionId" to inspectionId.toString())), repository, defects)

    private fun observationJson(defectId: UUID) =
        """{"id":"${UUID.randomUUID()}","defectId":"$defectId","inspectionId":"$inspectionId","status":"OPEN",
            "severity":"MAJOR","observedBy":"$propertyId","photos":[],"createdAt":"2026-11-06T10:00:00Z","version":0}"""
}
