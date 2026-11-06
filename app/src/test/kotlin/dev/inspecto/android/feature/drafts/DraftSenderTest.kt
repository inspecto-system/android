package dev.inspecto.android.feature.drafts

import dev.inspecto.android.api.apis.ChecklistApi
import dev.inspecto.android.api.apis.DefectsApi
import dev.inspecto.android.api.apis.InspectionsApi
import dev.inspecto.android.api.models.ChecklistItem
import dev.inspecto.android.api.models.CreateDefectRequest
import dev.inspecto.android.api.models.DefectDetails
import dev.inspecto.android.api.models.Observation
import dev.inspecto.android.core.db.PendingPhoto
import dev.inspecto.android.core.network.NetworkModule
import dev.inspecto.android.feature.inspections.InspectionFixtures
import dev.inspecto.android.feature.inspections.InspectionFixtures.cracks
import dev.inspecto.android.feature.inspections.InspectionFixtures.details
import dev.inspecto.android.feature.inspections.InspectionFixtures.inspectionId
import dev.inspecto.android.feature.inspections.InspectionFixtures.item
import dev.inspecto.android.feature.inspections.InspectionFixtures.kitchen
import dev.inspecto.android.feature.inspections.openDefect
import dev.inspecto.android.feature.photos.FakePendingPhotoDao
import dev.inspecto.android.feature.photos.QueueResult
import dev.inspecto.android.testing.TestDrafts
import dev.inspecto.android.testing.TestServer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Offline queue against a mock server: the owner's rules for conflicts, completion and photos (T-308). */
class DraftSenderTest {
    private val server = TestServer()
    private val json = NetworkModule.provideJson()
    private val drafts = TestDrafts()
    private val photos = FakePendingPhotoDao()
    private val sender =
        DraftSender(
            drafts.actions,
            photos,
            drafts.repository,
            drafts.notices,
            DraftApi(
                server.authorizedRetrofit.create(InspectionsApi::class.java),
                server.authorizedRetrofit.create(ChecklistApi::class.java),
                server.authorizedRetrofit.create(DefectsApi::class.java),
            ),
        )
    private val walls = item("Стены")

    @AfterEach
    fun close() = server.close()

    @Test
    fun shouldSendMarkWithVersionAndForgetIt() =
        runTest {
            drafts.repository.setResult(walls, ChecklistItem.Result.OK)
            server.enqueue(200, json.encodeToString(walls.copy(result = ChecklistItem.Result.OK, version = 1)))

            assertEquals(QueueResult.DONE, sender.sendReady())

            val patch = server.server.takeRequest()
            assertEquals("/api/v1/checklist-items/${walls.id}", patch.url.encodedPath)
            assertEquals("""{"version":0,"result":"OK"}""", patch.body?.utf8())
            assertTrue(
                drafts.actions.rows.value
                    .isEmpty(),
            )
        }

    @Test
    fun shouldResendInspectorsMarkWithFreshVersionAfterConflict() =
        runTest {
            drafts.repository.setResult(walls, ChecklistItem.Result.NOT_APPLICABLE)
            server.enqueue(409, TestServer.problem(409, "VERSION_CONFLICT"))
            // Someone else marked the item OK meanwhile; the inspector's mark still wins.
            server.enqueue(
                200,
                json.encodeToString(
                    details(kitchenItems = listOf(walls.copy(result = ChecklistItem.Result.OK, version = 5))),
                ),
            )
            server.enqueue(
                200,
                json.encodeToString(walls.copy(result = ChecklistItem.Result.NOT_APPLICABLE, version = 6)),
            )

            assertEquals(QueueResult.DONE, sender.sendReady())

            server.server.takeRequest()
            assertEquals(
                "/api/v1/inspections/$inspectionId",
                server.server
                    .takeRequest()
                    .url.encodedPath,
            )
            assertEquals(
                """{"version":5,"result":"NOT_APPLICABLE"}""",
                server.server
                    .takeRequest()
                    .body
                    ?.utf8(),
            )
            assertTrue(
                drafts.actions.rows.value
                    .isEmpty(),
            )
        }

    @Test
    fun shouldCreateDefectWithIdempotencyKeyAndReleaseItsPhotos() =
        runTest {
            val key = drafts.repository.createDefect(inspectionId, defectRequest())
            val local = DraftApplier.localId(key).toString()
            photos.insert(
                PendingPhoto(
                    "p",
                    "prop",
                    inspectionId.toString(),
                    null,
                    "p.jpg",
                    observationKey = local,
                    createdAt = 1,
                ),
            )
            val created = InspectionFixtures.observation(walls, "Трещина")
            server.enqueue(201, json.encodeToString(defectDetails(created.id)))

            assertEquals(QueueResult.DONE, sender.sendReady())

            val post = server.server.takeRequest()
            assertEquals("/api/v1/inspections/$inspectionId/defects", post.url.encodedPath)
            assertEquals(key, post.headers["Idempotency-Key"])
            assertEquals(created.id.toString(), photos.find("p")?.observationId)
            assertEquals(listOf("p"), photos.ready().map { it.id })
        }

    @Test
    fun shouldChangeObservationMadeMeanwhileInsteadOfFailing() =
        runTest {
            val crack = openDefect("Трещина")
            drafts.repository.recordObservation(inspectionId, crack.defect.id, Observation.Status.RESOLVED)
            val meanwhile = openDefect("Трещина", current = Observation.Status.OPEN)
            server.enqueue(409, TestServer.problem(409, "ALREADY_OBSERVED"))
            server.enqueue(200, json.encodeToString(listOf(meanwhile.copy(defect = crack.defect))))
            server.enqueue(
                200,
                json.encodeToString(
                    checkNotNull(meanwhile.currentObservation).copy(status = Observation.Status.RESOLVED),
                ),
            )

            assertEquals(QueueResult.DONE, sender.sendReady())

            assertEquals(
                "/api/v1/inspections/$inspectionId/observations",
                server.server
                    .takeRequest()
                    .url.encodedPath,
            )
            server.server.takeRequest()
            val patch = server.server.takeRequest()
            assertEquals("/api/v1/observations/${meanwhile.currentObservation.id}", patch.url.encodedPath)
            assertEquals("""{"version":2,"status":"RESOLVED"}""", patch.body?.utf8())
        }

    @Test
    fun shouldDropChangesWhenInspectionWasCompletedOnServer() =
        runTest {
            drafts.repository.setResult(walls, ChecklistItem.Result.OK)
            drafts.repository.setResult(item("Пол"), ChecklistItem.Result.OK)
            drafts.repository.createDefect(inspectionId, defectRequest())
            server.enqueue(409, TestServer.problem(409, "INVALID_STATE_TRANSITION"))

            assertEquals(QueueResult.DONE, sender.sendReady())

            assertTrue(
                drafts.actions.rows.value
                    .isEmpty(),
            )
            assertEquals(3, drafts.notices.observe(inspectionId).first())
            assertEquals(1, server.server.requestCount)
        }

    @Test
    fun shouldKeepChangesWhileOfflineAndSetAsideRejectedOnes() =
        runTest {
            drafts.repository.setResult(walls, ChecklistItem.Result.OK)
            drafts.repository.setResult(item("Пол"), ChecklistItem.Result.OK)
            server.enqueue(503, TestServer.problem(503, "DEPENDENCY_UNAVAILABLE"))

            assertEquals(QueueResult.RETRY, sender.sendReady())
            assertEquals(2, drafts.actions.rows.value.size)

            server.enqueue(400, TestServer.problem(400, "VALIDATION_ERROR"))
            server.enqueue(200, json.encodeToString(item("Пол").copy(result = ChecklistItem.Result.OK, version = 1)))
            assertEquals(QueueResult.DONE, sender.sendReady())

            val left =
                drafts.actions.rows.value
                    .single()
            assertEquals("VALIDATION_ERROR", left.errorCode)
            assertNull(drafts.actions.next())
        }

    private fun defectRequest() =
        CreateDefectRequest(
            roomId = kitchen.id,
            categoryId = cracks.id,
            elementGroup = CreateDefectRequest.ElementGroup.WALLS,
            title = "Трещина",
            severity = CreateDefectRequest.Severity.MAJOR,
            checklistItemId = walls.id,
        )

    private fun defectDetails(observationId: java.util.UUID): DefectDetails {
        val shown = InspectionFixtures.observation(walls, "Трещина")
        val defect = shown.defect
        return DefectDetails(
            id = defect.id,
            propertyId = defect.propertyId,
            roomId = defect.roomId,
            categoryId = defect.categoryId,
            elementGroup = DefectDetails.ElementGroup.WALLS,
            title = defect.title,
            severity = DefectDetails.Severity.MAJOR,
            currentStatus = DefectDetails.CurrentStatus.OPEN,
            firstInspectionId = inspectionId,
            createdAt = defect.createdAt,
            updatedAt = defect.updatedAt,
            version = 0,
            observations =
                listOf(
                    Observation(
                        id = observationId,
                        defectId = defect.id,
                        inspectionId = inspectionId,
                        status = Observation.Status.OPEN,
                        severity = Observation.Severity.MAJOR,
                        observedBy = shown.observedBy,
                        photos = emptyList(),
                        createdAt = shown.createdAt,
                        version = 0,
                    ),
                ),
        )
    }
}
