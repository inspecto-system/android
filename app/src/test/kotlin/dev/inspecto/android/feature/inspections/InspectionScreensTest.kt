package dev.inspecto.android.feature.inspections

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.SavedStateHandle
import dev.inspecto.android.api.models.ChecklistItem
import dev.inspecto.android.api.models.Inspection
import dev.inspecto.android.api.models.InspectionDetails
import dev.inspecto.android.api.models.PropertyDetails
import dev.inspecto.android.core.db.PendingPhoto
import dev.inspecto.android.feature.inspections.InspectionFixtures.bedroom
import dev.inspecto.android.feature.inspections.InspectionFixtures.cracks
import dev.inspecto.android.feature.inspections.InspectionFixtures.details
import dev.inspecto.android.feature.inspections.InspectionFixtures.inspection
import dev.inspecto.android.feature.inspections.InspectionFixtures.inspectionId
import dev.inspecto.android.feature.inspections.InspectionFixtures.item
import dev.inspecto.android.feature.inspections.InspectionFixtures.kitchen
import dev.inspecto.android.feature.inspections.InspectionFixtures.now
import dev.inspecto.android.feature.inspections.InspectionFixtures.observation
import dev.inspecto.android.feature.inspections.InspectionFixtures.propertyId
import dev.inspecto.android.feature.inspections.InspectionFixtures.standard
import dev.inspecto.android.feature.photos.PhotoQueueViewModel
import dev.inspecto.android.feature.photos.PhotoRepository
import dev.inspecto.android.feature.properties.PropertyRepository
import dev.inspecto.android.media.api.models.PhotoLimits
import dev.inspecto.android.testing.TestDrafts
import dev.inspecto.android.ui.theme.InspectoTheme
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Inspection screens with real view models over a mocked repository (Robolectric, Russian locale). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "ru")
class InspectionScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val repository = mockk<InspectionRepository>(relaxed = true)
    private val defects = mockk<DefectRepository>(relaxed = true)

    @Test
    fun newInspectionListsRoomsAndTemplates() {
        val properties = mockk<PropertyRepository>()
        coEvery { properties.details(propertyId) } returns
            PropertyDetails(
                name = "Квартира",
                type = PropertyDetails.Type.APARTMENT,
                address = "ул. Ленина, 1",
                id = propertyId,
                createdAt = now,
                updatedAt = now,
                version = 0,
                rooms = listOf(kitchen, bedroom),
                inspectionCount = 0,
                openDefectCount = 0,
            )
        coEvery { repository.templates() } returns listOf(standard)
        val viewModel =
            CreateInspectionViewModel(
                SavedStateHandle(mapOf("propertyId" to propertyId.toString())),
                properties,
                repository,
            )
        compose.setContent {
            InspectoTheme {
                CreateInspectionScreen(
                    onBack = {},
                    onCreated = {},
                    viewModel = viewModel,
                )
            }
        }

        compose.onNodeWithText("Стандартный").assertIsDisplayed()
        compose.onNodeWithText("Спальня").assertIsDisplayed()
        compose.onNodeWithText("Создать осмотр").performClick()
        compose.waitForIdle()
        coVerify { repository.create(propertyId, any()) }
    }

    @Test
    fun draftInspectionCanOnlyBeStarted() {
        coEvery { repository.details(inspectionId) } returns details(InspectionDetails.Status.DRAFT)
        coEvery { repository.start(any()) } returns inspection(Inspection.Status.IN_PROGRESS)
        showInspection()

        compose.onNodeWithText("Черновик").assertIsDisplayed()
        compose.onNodeWithText("OK").assertIsNotEnabled()
        compose.onNodeWithText("Начать осмотр").performClick()
        compose.waitForIdle()
        coVerify { repository.start(any()) }
    }

    @Test
    fun defectMarkOpensDefectDialog() {
        val walls = item("Стены без трещин")
        coEvery { repository.details(inspectionId) } returns details(kitchenItems = listOf(walls))
        coEvery { defects.categories() } returns listOf(cracks)
        showInspection()

        compose.onNodeWithText("Дефект").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Новый дефект").assertIsDisplayed()
        compose.onNodeWithText("Трещины").assertIsDisplayed()
        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithText("Описать дефект").assertIsDisplayed()
    }

    @Test
    fun roomTabsSwitchChecklistAndShowDefects() {
        val walls = item("Стены без трещин", result = ChecklistItem.Result.DEFECT)
        coEvery { repository.details(inspectionId) } returns
            details(kitchenItems = listOf(walls), observations = listOf(observation(walls, "Трещина у окна")))
        coEvery { defects.categories() } returns listOf(cracks)
        showInspection()

        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Трещина у окна"))
        compose.onNodeWithText("Трещина у окна").assertIsDisplayed()
        compose.onNodeWithText("Спальня").performClick()
        compose.onNodeWithText("Окно открывается").assertIsDisplayed()
    }

    @Test
    fun completionAsksAndWarnsAboutUncheckedItems() {
        coEvery { repository.details(inspectionId) } returns details()
        coEvery { repository.complete(any()) } returns inspection(Inspection.Status.COMPLETED)
        showInspection()

        compose.onNodeWithText("Завершить осмотр").performClick()
        compose.onNodeWithText("Не отмечено пунктов: 2").assertIsDisplayed()
        compose.onAllNodesWithText("Завершить осмотр")[1].performClick()
        compose.waitForIdle()
        coVerify { repository.complete(any()) }
    }

    @Test
    fun repeatInspectionAsksToMarkEarlierDefects() {
        val crack = openDefect("Трещина с прошлого раза")
        coEvery { repository.details(inspectionId) } returns details()
        coEvery { defects.openDefects(inspectionId) } returns listOf(crack)
        coEvery { repository.complete(any()) } returns inspection(Inspection.Status.COMPLETED)
        showInspection()

        compose.onNodeWithText("Дефекты прошлых осмотров").assertIsDisplayed()
        compose.onNodeWithText("Не отмечен").assertIsDisplayed()
        compose.onNodeWithText("Устранён").performClick()
        compose.waitForIdle()
        check(
            drafts.actions.rows.value
                .single()
                .payload
                .contains("RESOLVED"),
        )

        // The decision is still on the device: completion waits for it (owner's rule 3).
        compose.onNodeWithText("Изменений ждут отправки: 1. Завершить осмотр можно после отправки.").assertIsDisplayed()
        compose.onNodeWithText("Завершить осмотр").assertIsNotEnabled()
    }

    @Test
    fun completionWarnsAboutUnmarkedEarlierDefects() {
        coEvery { repository.details(inspectionId) } returns details()
        coEvery { defects.openDefects(inspectionId) } returns listOf(openDefect("Трещина с прошлого раза"))
        showInspection()

        compose.onNodeWithText("Завершить осмотр").performClick()
        compose.onNodeWithText("Не отмечено дефектов прошлых осмотров: 1").assertIsDisplayed()
    }

    @Test
    fun showsThatServerCompletedTheInspection() {
        coEvery { repository.details(inspectionId) } returns details()
        showInspection()
        runBlocking { drafts.notices.completedElsewhere(inspectionId, dropped = 2) }

        compose
            .onNodeWithText(
                "Осмотр уже завершён на сервере. Неотправленные изменения отменены: 2.",
            ).assertIsDisplayed()
        compose.onNodeWithText("Понятно").performClick()
        compose.waitForIdle()
        check(
            drafts.cache.rows.value.keys
                .none { it.startsWith("completed-elsewhere") },
        )
    }

    @Test
    fun observationOffersCameraAndQueuedPhotoBlocksCompletion() {
        val walls = item("Стены без трещин", result = ChecklistItem.Result.DEFECT)
        val crack = observation(walls, "Трещина у окна")
        coEvery { repository.details(inspectionId) } returns
            details(kitchenItems = listOf(walls), observations = listOf(crack))
        coEvery { photoRepository.limits() } returns
            PhotoLimits(listOf(PhotoLimits.AllowedContentTypes.IMAGE_SLASH_JPEG), 15_728_640, 50_000_000, 10)
        var camera: PhotoRequest? = null
        showInspection { camera = it }

        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Сделать фото (0 из 10)"))
        compose.onNodeWithText("Сделать фото (0 из 10)").performClick()
        check(camera == PhotoRequest(propertyId, crack.id, attached = 0, local = false))

        queue.value =
            listOf(
                PendingPhoto(
                    "p1",
                    propertyId.toString(),
                    inspectionId.toString(),
                    crack.id.toString(),
                    "missing.jpg",
                    createdAt = 1,
                ),
            )
        compose.onNodeWithText("Фото ещё не отправлено: 1. Завершить осмотр можно после отправки.").assertIsDisplayed()
        compose.onNodeWithText("Завершить осмотр").assertIsNotEnabled()
    }

    private val photoRepository = mockk<PhotoRepository>(relaxed = true)
    private val queue = MutableStateFlow<List<PendingPhoto>>(emptyList())

    private val drafts = TestDrafts()

    private fun showInspection(onTakePhoto: (PhotoRequest) -> Unit = {}) {
        every { photoRepository.pending(inspectionId) } returns queue
        val viewModel =
            InspectionViewModel(
                SavedStateHandle(mapOf("inspectionId" to inspectionId.toString())),
                repository,
                defects,
                drafts.repository,
            )
        val photos =
            PhotoQueueViewModel(
                SavedStateHandle(mapOf("inspectionId" to inspectionId.toString())),
                photoRepository,
                drafts.repository,
                drafts.notices,
            )
        compose.setContent {
            InspectoTheme {
                InspectionScreen(
                    onBack = {},
                    onReports = {},
                    onTakePhoto = onTakePhoto,
                    viewModel = viewModel,
                    photos = photos,
                )
            }
        }
    }
}
