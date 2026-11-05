package dev.inspecto.android.feature

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import dev.inspecto.android.api.models.Property
import dev.inspecto.android.api.models.PropertyDetails
import dev.inspecto.android.api.models.Room
import dev.inspecto.android.core.auth.SessionRepository
import dev.inspecto.android.feature.auth.LoginScreen
import dev.inspecto.android.feature.auth.LoginViewModel
import dev.inspecto.android.feature.auth.RegisterScreen
import dev.inspecto.android.feature.auth.RegisterViewModel
import dev.inspecto.android.feature.properties.CreatePropertyScreen
import dev.inspecto.android.feature.properties.CreatePropertyViewModel
import dev.inspecto.android.feature.properties.PropertyDetailsScreen
import dev.inspecto.android.feature.properties.PropertyDetailsViewModel
import dev.inspecto.android.feature.properties.PropertyListScreen
import dev.inspecto.android.feature.properties.PropertyListViewModel
import dev.inspecto.android.feature.properties.PropertyRepository
import dev.inspecto.android.ui.permission.LocalNetworkPermissionGate
import dev.inspecto.android.ui.permission.LocalNetworkPermissionScreen
import dev.inspecto.android.ui.theme.InspectoTheme
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.OffsetDateTime
import java.util.UUID

/** Screens with real view models over mocked data sources (Robolectric, Russian locale). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "ru")
class ScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val session = mockk<SessionRepository>(relaxed = true)
    private val repository = mockk<PropertyRepository>(relaxed = true)
    private val now = OffsetDateTime.parse("2026-11-05T10:00:00Z")
    private val propertyId = UUID.randomUUID()

    @Test
    fun loginShowsValidationAndSignsIn() {
        val viewModel = LoginViewModel(session)
        compose.setContent { InspectoTheme { LoginScreen(onRegister = {}, viewModel = viewModel) } }

        compose.onNodeWithText("Войти").performClick()
        compose.onNodeWithText("Введите email").assertIsDisplayed()

        compose.onNodeWithText("Email").performTextInput("user@example.com")
        compose.onNodeWithText("Пароль").performTextInput("Kitchen-Window-42")
        compose.onNodeWithText("Войти").performClick()
        compose.waitForIdle()
        coVerify { session.login("user@example.com", "Kitchen-Window-42") }
    }

    @Test
    fun registrationExplainsPasswordRule() {
        var toLogin = false
        val viewModel = RegisterViewModel(session)
        compose.setContent {
            InspectoTheme { RegisterScreen(onLogin = { toLogin = true }, viewModel = viewModel) }
        }

        compose.onNodeWithText("Не короче 10 символов").assertIsDisplayed()
        compose.onNodeWithText("Зарегистрироваться").performClick()
        compose.onNodeWithText("Пароль должен быть не короче 10 символов").assertIsDisplayed()
        compose.onNodeWithText("Уже есть аккаунт? Войти").performScrollTo().performClick()
        check(toLogin)
    }

    @Test
    fun propertyListShowsPropertiesAndOpensOne() {
        coEvery { repository.list(any()) } returns listOf(property())
        var opened: UUID? = null
        val viewModel = PropertyListViewModel(repository, session)
        compose.setContent {
            InspectoTheme {
                PropertyListScreen(onCreate = {}, onOpen = { opened = it }, viewModel = viewModel)
            }
        }

        compose.onNodeWithText("Квартира на Ленина").assertIsDisplayed()
        compose.onNodeWithText("Квартира").assertIsDisplayed()
        compose.onNodeWithText("Квартира на Ленина").performClick()
        check(opened == propertyId)
    }

    @Test
    fun propertyListShowsEmptyStateAndSignsOut() {
        coEvery { repository.list(any()) } returns emptyList()
        val viewModel = PropertyListViewModel(repository, session)
        compose.setContent {
            InspectoTheme {
                PropertyListScreen(onCreate = {}, onOpen = {}, viewModel = viewModel)
            }
        }

        compose.onNodeWithText("Объектов пока нет. Добавьте первый.").assertIsDisplayed()
        compose.onNodeWithText("Выйти").performClick()
        compose.waitForIdle()
        coVerify { session.logout() }
    }

    @Test
    fun newPropertyFormRequiresNameAndAddress() {
        val viewModel = CreatePropertyViewModel(repository)
        compose.setContent {
            InspectoTheme {
                CreatePropertyScreen(onBack = {}, onCreated = {}, viewModel = viewModel)
            }
        }

        compose.onNodeWithText("Дом").performClick()
        compose.onNodeWithText("Сохранить").performClick()
        compose.onNodeWithText("Название").assertIsDisplayed()
        coVerify(exactly = 0) { repository.create(any()) }
    }

    @Test
    fun propertyCardShowsRoomsAndAddsOne() {
        coEvery { repository.details(propertyId) } returns details()
        val viewModel = detailsViewModel()
        compose.setContent {
            InspectoTheme {
                PropertyDetailsScreen(onBack = {}, viewModel = viewModel)
            }
        }

        compose.onNodeWithText("Моя кухня").assertIsDisplayed()
        compose.onNodeWithText("54.3 м²").assertIsDisplayed()
        compose.onNodeWithText("Добавить помещение").performClick()
        compose.onNodeWithText("Название помещения").performTextInput("Детская")
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitForIdle()
        coVerify { repository.addRoom(propertyId, "Детская", any()) }
    }

    @Test
    fun propertyCardAsksBeforeDeletingRoom() {
        coEvery { repository.details(propertyId) } returns details()
        val viewModel = detailsViewModel()
        compose.setContent {
            InspectoTheme {
                PropertyDetailsScreen(onBack = {}, viewModel = viewModel)
            }
        }

        compose.onNodeWithText("Удалить").performClick()
        compose.onNodeWithText("Удалить помещение?").assertIsDisplayed()
        compose.onNodeWithText("Отмена").performClick()
        coVerify(exactly = 0) { repository.deleteRoom(any()) }
    }

    @Test
    fun localNetworkScreenExplainsAndOffersSettingsAfterRefusal() {
        var requested = false
        var settings = false
        compose.setContent {
            InspectoTheme {
                LocalNetworkPermissionScreen(denied = true, onRequest = { requested = true }, onOpenSettings = {
                    settings =
                        true
                })
            }
        }

        compose.onNodeWithText("Доступ к локальной сети").assertIsDisplayed()
        compose.onNodeWithText("Разрешить").performClick()
        compose.onNodeWithText("Открыть настройки").performClick()
        check(requested && settings)
    }

    @Test
    fun localNetworkGateShowsAppBelowAndroid17() {
        compose.setContent {
            InspectoTheme {
                LocalNetworkPermissionGate {
                    androidx.compose.material3.Text(
                        "Приложение",
                    )
                }
            }
        }

        compose.onNodeWithText("Приложение").assertIsDisplayed()
    }

    private fun detailsViewModel() =
        PropertyDetailsViewModel(SavedStateHandle(mapOf("propertyId" to propertyId.toString())), repository)

    private fun property() =
        Property(
            name = "Квартира на Ленина",
            type = Property.Type.APARTMENT,
            address = "ул. Ленина, 1",
            id = propertyId,
            createdAt = now,
            updatedAt = now,
            version = 0,
        )

    private fun details() =
        PropertyDetails(
            name = "Квартира на Ленина",
            type = PropertyDetails.Type.APARTMENT,
            address = "ул. Ленина, 1",
            id = propertyId,
            createdAt = now,
            updatedAt = now,
            version = 0,
            rooms = listOf(Room(UUID.randomUUID(), propertyId, "Моя кухня", Room.RoomType.KITCHEN, 0, 0)),
            inspectionCount = 0,
            openDefectCount = 0,
            areaSqm = java.math.BigDecimal("54.3"),
        )
}
