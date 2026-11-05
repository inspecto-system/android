package dev.inspecto.android.feature.properties

import androidx.lifecycle.SavedStateHandle
import dev.inspecto.android.R
import dev.inspecto.android.api.models.CreatePropertyRequest
import dev.inspecto.android.api.models.CreateRoomRequest
import dev.inspecto.android.api.models.Property
import dev.inspecto.android.api.models.PropertyDetails
import dev.inspecto.android.api.models.Room
import dev.inspecto.android.core.auth.SessionRepository
import dev.inspecto.android.core.error.AppError
import dev.inspecto.android.testing.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.IOException
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class PropertyViewModelsTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val repository = mockk<PropertyRepository>(relaxed = true)
    private val propertyId: UUID = UUID.randomUUID()
    private val now: OffsetDateTime = OffsetDateTime.parse("2026-11-05T10:00:00Z")

    @Test
    fun shouldLoadPropertiesAndSearchAfterTypingPause() =
        runTest(main.dispatcher) {
            coEvery { repository.list(any()) } returns listOf(property())
            val viewModel = PropertyListViewModel(repository, mockk(relaxed = true))

            viewModel.reload()
            advanceUntilIdle()
            assertEquals(1, viewModel.state.value.items.size)
            assertFalse(viewModel.state.value.loading)

            viewModel.onQueryChange("Ле")
            viewModel.onQueryChange("Ленина")
            advanceTimeBy(100)
            coVerify(exactly = 1) { repository.list(any()) }
            advanceUntilIdle()
            coVerify(exactly = 1) { repository.list("Ленина") }
        }

    @Test
    fun shouldShowListErrorAndSignOut() =
        runTest(main.dispatcher) {
            coEvery { repository.list(any()) } throws AppError.Network(IOException())
            val session = mockk<SessionRepository>(relaxed = true)
            val viewModel = PropertyListViewModel(repository, session)

            viewModel.reload()
            advanceUntilIdle()
            viewModel.signOut()
            advanceUntilIdle()

            assertEquals(R.string.error_network, viewModel.state.value.errorRes)
            coVerify { session.logout() }
        }

    @Test
    fun shouldValidateNewProperty() =
        runTest(main.dispatcher) {
            val viewModel = CreatePropertyViewModel(repository)
            viewModel.onAreaChange("-5")

            viewModel.save()
            advanceUntilIdle()

            assertEquals(R.string.validation_required, viewModel.state.value.nameError)
            assertEquals(R.string.validation_required, viewModel.state.value.addressError)
            assertEquals(R.string.validation_area, viewModel.state.value.areaError)
            coVerify(exactly = 0) { repository.create(any()) }
        }

    @Test
    fun shouldCreatePropertyWithTrimmedFields() =
        runTest(main.dispatcher) {
            val request = slot<CreatePropertyRequest>()
            coEvery { repository.create(capture(request)) } returns property()
            val viewModel = CreatePropertyViewModel(repository)
            viewModel.onNameChange(" Дача ")
            viewModel.onTypeChange(CreatePropertyRequest.Type.HOUSE)
            viewModel.onAddressChange(" пос. Лесной ")
            viewModel.onCityChange(" ")
            viewModel.onAreaChange("54,3")
            viewModel.onNotesChange("Баня")

            viewModel.save()
            advanceUntilIdle()

            assertEquals(
                CreatePropertyRequest(
                    name = "Дача",
                    type = CreatePropertyRequest.Type.HOUSE,
                    address = "пос. Лесной",
                    areaSqm = BigDecimal("54.3"),
                    notes = "Баня",
                ),
                request.captured,
            )
            assertEquals(propertyId, viewModel.state.value.createdId)
        }

    @Test
    fun shouldShowCreationError() =
        runTest(main.dispatcher) {
            coEvery { repository.create(any()) } throws AppError.Api(400, "VALIDATION_ERROR")
            val viewModel = CreatePropertyViewModel(repository)
            viewModel.onNameChange("Дача")
            viewModel.onAddressChange("пос. Лесной")

            viewModel.save()
            advanceUntilIdle()

            assertEquals(R.string.error_validation, viewModel.state.value.errorRes)
            assertNull(viewModel.state.value.createdId)
        }

    @ParameterizedTest
    @CsvSource(
        value = ["54.3|54.3", "0|0", "100000|100000", "12,25|12.25", "100000.01|", "1.234|", "abc|", "-1|"],
        delimiter = '|',
    )
    fun shouldParseArea(
        text: String,
        expected: String?,
    ) {
        assertEquals(expected?.let(::BigDecimal), CreatePropertyViewModel.parseArea(text))
    }

    @Test
    fun shouldLoadCardAndManageRooms() =
        runTest(main.dispatcher) {
            val room = room("Кухня")
            coEvery { repository.details(propertyId) } returns details(listOf(room))
            val viewModel =
                PropertyDetailsViewModel(SavedStateHandle(mapOf("propertyId" to propertyId.toString())), repository)
            advanceUntilIdle()
            assertEquals(
                listOf(room),
                viewModel.state.value.property
                    ?.rooms,
            )

            viewModel.addRoom("Ванная", CreateRoomRequest.RoomType.BATHROOM)
            viewModel.renameRoom(room, "Столовая")
            viewModel.deleteRoom(room)
            advanceUntilIdle()

            coVerify { repository.addRoom(propertyId, "Ванная", CreateRoomRequest.RoomType.BATHROOM) }
            coVerify { repository.renameRoom(room, "Столовая") }
            coVerify { repository.deleteRoom(room.id) }
            coVerify(exactly = 4) { repository.details(propertyId) }
        }

    @Test
    fun shouldShowErrorOfRoomChange() =
        runTest(main.dispatcher) {
            coEvery { repository.details(propertyId) } returns details(emptyList())
            coEvery { repository.addRoom(any(), any(), any()) } throws AppError.Api(404, "NOT_FOUND")
            val viewModel =
                PropertyDetailsViewModel(SavedStateHandle(mapOf("propertyId" to propertyId.toString())), repository)

            viewModel.addRoom("Кухня", CreateRoomRequest.RoomType.KITCHEN)
            advanceUntilIdle()

            assertEquals(R.string.error_not_found, viewModel.state.value.errorRes)
            assertFalse(viewModel.state.value.loading)
        }

    private fun property() =
        Property(
            name = "Квартира",
            type = Property.Type.APARTMENT,
            address = "ул. Мира, 1",
            id = propertyId,
            createdAt = now,
            updatedAt = now,
            version = 0,
        )

    private fun room(name: String) =
        Room(
            id = UUID.randomUUID(),
            propertyId = propertyId,
            name = name,
            roomType = Room.RoomType.KITCHEN,
            sortOrder = 0,
            version = 0,
        )

    private fun details(rooms: List<Room>) =
        PropertyDetails(
            name = "Квартира",
            type = PropertyDetails.Type.APARTMENT,
            address = "ул. Мира, 1",
            id = propertyId,
            createdAt = now,
            updatedAt = now,
            version = 0,
            rooms = rooms,
            inspectionCount = 0,
            openDefectCount = 0,
        )
}
