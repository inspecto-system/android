package dev.inspecto.android.feature.comparison

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.inspecto.android.R
import dev.inspecto.android.api.apis.ComparisonApi
import dev.inspecto.android.api.models.ComparedDefect
import dev.inspecto.android.api.models.InspectionComparison
import dev.inspecto.android.api.models.InspectionSummary
import dev.inspecto.android.core.error.AppError
import dev.inspecto.android.core.error.apiCall
import dev.inspecto.android.core.error.messageRes
import dev.inspecto.android.feature.inspections.InspectionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Comparison of two inspections of a property (platform/api/core.yaml, tag comparison, ADR-004). */
class ComparisonRepository
    @Inject
    constructor(
        private val api: ComparisonApi,
    ) {
        suspend fun compare(
            base: UUID,
            target: UUID,
        ): InspectionComparison = apiCall { api.compareInspections(base, target) }
    }

data class ComparisonState(
    /** Inspections of the property, oldest first. */
    val inspections: List<InspectionSummary> = emptyList(),
    val base: UUID? = null,
    val target: UUID? = null,
    val comparison: InspectionComparison? = null,
    /** Only defects in this state are listed; null lists all. */
    val filter: ComparedDefect.State? = null,
    val loading: Boolean = true,
    @StringRes val errorRes: Int? = null,
) {
    val visibleDefects: List<ComparedDefect>
        get() = comparison?.defects.orEmpty().filter { filter == null || it.state == filter }
}

/** T-405: picks two inspections (the two latest by default) and shows what changed between them. */
@HiltViewModel
class ComparisonViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val inspections: InspectionRepository,
        private val repository: ComparisonRepository,
    ) : ViewModel() {
        private val propertyId: UUID = UUID.fromString(checkNotNull(savedStateHandle.get<String>("propertyId")))
        private val mutableState = MutableStateFlow(ComparisonState())
        private var request: Job? = null

        val state: StateFlow<ComparisonState> = mutableState.asStateFlow()

        init {
            reload()
        }

        fun reload() {
            viewModelScope.launch {
                mutableState.update { it.copy(loading = true, errorRes = null) }
                try {
                    val all = inspections.list(propertyId).sortedBy { it.inspectedAt }
                    mutableState.update {
                        it.copy(
                            inspections = all,
                            base = all.getOrNull(all.size - 2)?.id,
                            target = all.lastOrNull()?.id,
                        )
                    }
                    compare()
                } catch (e: AppError) {
                    mutableState.update { it.copy(loading = false, errorRes = e.messageRes()) }
                }
            }
        }

        fun selectBase(id: UUID) {
            mutableState.update { it.copy(base = id) }
            compare()
        }

        fun selectTarget(id: UUID) {
            mutableState.update { it.copy(target = id) }
            compare()
        }

        /** Shows only defects in [state]; choosing the current filter again shows all. */
        fun toggleFilter(state: ComparedDefect.State) =
            mutableState.update { it.copy(filter = if (it.filter == state) null else state) }

        private fun compare() {
            val current = state.value
            val base = current.inspections.find { it.id == current.base }
            val target = current.inspections.find { it.id == current.target }
            request?.cancel()
            if (base == null || target == null) {
                mutableState.update { it.copy(loading = false, comparison = null) }
                return
            }
            // The API requires A to be earlier than B; checked here so that no request is wasted.
            if (!base.inspectedAt.isBefore(target.inspectedAt)) {
                mutableState.update { it.copy(loading = false, comparison = null, errorRes = R.string.compare_order) }
                return
            }
            request =
                viewModelScope.launch {
                    mutableState.update { it.copy(loading = true, errorRes = null) }
                    try {
                        val comparison = repository.compare(base.id, target.id)
                        mutableState.update { it.copy(loading = false, comparison = comparison) }
                    } catch (e: AppError) {
                        mutableState.update { it.copy(loading = false, comparison = null, errorRes = e.messageRes()) }
                    }
                }
        }
    }
