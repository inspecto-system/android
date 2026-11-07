package dev.inspecto.android.feature.comparison

import androidx.lifecycle.SavedStateHandle
import dev.inspecto.android.R
import dev.inspecto.android.api.apis.ComparisonApi
import dev.inspecto.android.api.models.ComparedDefect
import dev.inspecto.android.core.error.AppError
import dev.inspecto.android.feature.comparison.ComparisonFixtures.comparison
import dev.inspecto.android.feature.comparison.ComparisonFixtures.defect
import dev.inspecto.android.feature.comparison.ComparisonFixtures.inspection
import dev.inspecto.android.feature.comparison.ComparisonFixtures.propertyId
import dev.inspecto.android.feature.inspections.InspectionRepository
import dev.inspecto.android.testing.MainDispatcherExtension
import dev.inspecto.android.testing.TestServer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ComparisonViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val inspections = mockk<InspectionRepository>()
    private val repository = mockk<ComparisonRepository>()
    private val first = inspection("2026-09-01", "Приёмка")
    private val second = inspection("2026-10-01")
    private val third = inspection("2026-11-01", "Повторный")
    private val resolved = defect("Трещина у окна", ComparedDefect.State.RESOLVED)
    private val remaining = defect("Царапина", ComparedDefect.State.REMAINING, inKitchen = false)

    private fun viewModel() =
        ComparisonViewModel(SavedStateHandle(mapOf("propertyId" to propertyId.toString())), inspections, repository)

    @Test
    fun shouldCompareTwoLatestInspectionsByDefault() =
        runTest(main.dispatcher) {
            // The API lists newest first; the screen orders them by date.
            coEvery { inspections.list(propertyId) } returns listOf(third, first, second)
            coEvery { repository.compare(second.id, third.id) } returns
                comparison(second, third, listOf(resolved, remaining))

            val viewModel = viewModel()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(listOf(first, second, third), state.inspections)
            assertEquals(second.id, state.base)
            assertEquals(third.id, state.target)
            assertEquals(1, state.comparison?.summary?.resolved)
            assertEquals(2, state.visibleDefects.size)
        }

    @Test
    fun shouldFilterByStateAndShowAllOnSecondTap() =
        runTest(main.dispatcher) {
            coEvery { inspections.list(propertyId) } returns listOf(first, third)
            coEvery { repository.compare(first.id, third.id) } returns
                comparison(first, third, listOf(resolved, remaining))
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.toggleFilter(ComparedDefect.State.REMAINING)
            assertEquals(listOf(remaining), viewModel.state.value.visibleDefects)
            viewModel.toggleFilter(ComparedDefect.State.REMAINING)
            assertEquals(2, viewModel.state.value.visibleDefects.size)
        }

    @Test
    fun shouldNotRequestWhenBaseIsNotEarlierAndRecompareOnValidChoice() =
        runTest(main.dispatcher) {
            coEvery { inspections.list(propertyId) } returns listOf(first, second, third)
            coEvery { repository.compare(any(), any()) } answers
                { comparison(first, third, listOf(resolved)) }
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.selectBase(third.id)
            advanceUntilIdle()
            assertEquals(R.string.compare_order, viewModel.state.value.errorRes)
            assertNull(viewModel.state.value.comparison)
            coVerify(exactly = 0) { repository.compare(third.id, any()) }

            viewModel.selectBase(first.id)
            advanceUntilIdle()
            assertNull(viewModel.state.value.errorRes)
            coVerify { repository.compare(first.id, third.id) }
        }

    @Test
    fun shouldShowErrorsAndNotCompareSingleInspection() =
        runTest(main.dispatcher) {
            coEvery { inspections.list(propertyId) } returns listOf(first)
            val single = viewModel()
            advanceUntilIdle()
            assertNull(single.state.value.base)
            coVerify(exactly = 0) { repository.compare(any(), any()) }

            coEvery { inspections.list(propertyId) } throws AppError.Network(IOException())
            val offline = viewModel()
            advanceUntilIdle()
            assertEquals(R.string.error_network, offline.state.value.errorRes)

            coEvery { inspections.list(propertyId) } returns listOf(first, third)
            coEvery { repository.compare(any(), any()) } throws AppError.Api(404, "NOT_FOUND")
            val gone = viewModel()
            advanceUntilIdle()
            assertEquals(R.string.error_not_found, gone.state.value.errorRes)
        }

    @Test
    fun repositoryCallsComparisonEndpoint() =
        runTest {
            val server = TestServer()
            try {
                server.enqueue(500, TestServer.problem(500, "INTERNAL"))
                val api = server.authorizedRetrofit.create(ComparisonApi::class.java)

                runCatching { ComparisonRepository(api).compare(first.id, third.id) }

                val request = server.server.takeRequest()
                assertEquals("/api/v1/inspections/compare", request.url.encodedPath)
                assertEquals("base=${first.id}&target=${third.id}", request.url.query)
            } finally {
                server.close()
            }
        }
}
