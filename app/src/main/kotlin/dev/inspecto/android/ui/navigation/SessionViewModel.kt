package dev.inspecto.android.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.inspecto.android.core.auth.SessionRepository
import dev.inspecto.android.core.auth.SessionState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Session state that decides between the sign-in screens and the main screens. */
@HiltViewModel
class SessionViewModel
    @Inject
    constructor(
        private val session: SessionRepository,
    ) : ViewModel() {
        val state: StateFlow<SessionState> = session.state

        init {
            viewModelScope.launch { session.restore() }
        }
    }
