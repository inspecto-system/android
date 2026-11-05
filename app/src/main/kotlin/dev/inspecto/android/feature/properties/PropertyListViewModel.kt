package dev.inspecto.android.feature.properties

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.inspecto.android.api.models.Property
import dev.inspecto.android.core.auth.SessionRepository
import dev.inspecto.android.core.error.AppError
import dev.inspecto.android.core.error.messageRes
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PropertyListState(
    val query: String = "",
    val items: List<Property> = emptyList(),
    val loading: Boolean = true,
    @StringRes val errorRes: Int? = null,
)

@HiltViewModel
class PropertyListViewModel
    @Inject
    constructor(
        private val repository: PropertyRepository,
        private val session: SessionRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(PropertyListState())
        private var loadJob: Job? = null

        val state: StateFlow<PropertyListState> = mutableState.asStateFlow()

        init {
            observeQuery()
        }

        fun onQueryChange(value: String) = mutableState.update { it.copy(query = value) }

        /** Reloads the list, for example after returning from another screen. */
        fun reload() {
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    mutableState.update { it.copy(loading = true, errorRes = null) }
                    try {
                        val items = repository.list(state.value.query)
                        mutableState.update { it.copy(items = items, loading = false) }
                    } catch (e: AppError) {
                        mutableState.update { it.copy(loading = false, errorRes = e.messageRes()) }
                    }
                }
        }

        fun signOut() {
            viewModelScope.launch { session.logout() }
        }

        @OptIn(FlowPreview::class)
        private fun observeQuery() {
            viewModelScope.launch {
                state
                    .map { it.query.trim() }
                    .distinctUntilChanged()
                    .drop(1)
                    .debounce(SEARCH_DEBOUNCE_MS)
                    .collect { reload() }
            }
        }

        private companion object {
            const val SEARCH_DEBOUNCE_MS = 300L
        }
    }
