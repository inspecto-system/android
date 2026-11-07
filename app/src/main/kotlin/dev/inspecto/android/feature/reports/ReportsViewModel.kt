package dev.inspecto.android.feature.reports

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.inspecto.android.R
import dev.inspecto.android.core.error.AppError
import dev.inspecto.android.core.error.messageRes
import dev.inspecto.android.report.api.models.ReportJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class ReportsState(
    val reports: List<ReportJob> = emptyList(),
    val loading: Boolean = true,
    /** A report is being ordered and generated. */
    val generating: Boolean = false,
    /** The report whose file is being downloaded. */
    val downloading: UUID? = null,
    /** A downloaded file the screen opens in a PDF viewer, then reports with [ReportsViewModel.opened]. */
    val fileToOpen: File? = null,
    @StringRes val errorRes: Int? = null,
)

/** Reports of one inspection: order, wait until generated, download and open (T-404). */
@HiltViewModel
class ReportsViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: ReportRepository,
    ) : ViewModel() {
        private val inspectionId: UUID = UUID.fromString(checkNotNull(savedStateHandle.get<String>("inspectionId")))
        private val mutableState = MutableStateFlow(ReportsState())

        /** Key of an order whose answer has not arrived; repeating it does not create a second report. */
        private var pendingKey: String? = null

        val state: StateFlow<ReportsState> = mutableState.asStateFlow()

        init {
            reload()
        }

        fun reload() {
            viewModelScope.launch {
                mutableState.update { it.copy(loading = true, errorRes = null) }
                try {
                    val reports = repository.list(inspectionId)
                    mutableState.update { it.copy(reports = reports, loading = false) }
                } catch (e: AppError) {
                    mutableState.update { it.copy(loading = false, errorRes = e.messageRes()) }
                }
            }
        }

        /** Orders a report, waits for it as the contract says (every 2 s, up to 60 s) and opens it when ready. */
        fun order() {
            if (state.value.generating) return
            viewModelScope.launch {
                mutableState.update { it.copy(generating = true, errorRes = null) }
                try {
                    val key = pendingKey ?: UUID.randomUUID().toString().also { pendingKey = it }
                    val accepted = repository.request(inspectionId, key)
                    pendingKey = null
                    val finished = waitUntilFinished(accepted)
                    mutableState.update {
                        it.copy(reports = repository.list(inspectionId), errorRes = finished.problemRes())
                    }
                    if (finished.status == ReportJob.Status.DONE) {
                        fetch(finished.id)
                    }
                } catch (e: AppError) {
                    mutableState.update { it.copy(errorRes = e.messageRes()) }
                } finally {
                    mutableState.update { it.copy(generating = false) }
                }
            }
        }

        /** Downloads the report if it is not on the device yet and opens it. */
        fun open(report: ReportJob) {
            if (report.status != ReportJob.Status.DONE || state.value.downloading != null) return
            viewModelScope.launch {
                mutableState.update { it.copy(errorRes = null) }
                try {
                    fetch(report.id)
                } catch (e: AppError) {
                    mutableState.update { it.copy(errorRes = e.messageRes()) }
                }
            }
        }

        /** The screen has handed [ReportsState.fileToOpen] to a viewer. */
        fun opened() = mutableState.update { it.copy(fileToOpen = null) }

        /** No app on the device can show a PDF. */
        fun noViewer() = mutableState.update { it.copy(fileToOpen = null, errorRes = R.string.report_no_viewer) }

        private suspend fun fetch(reportId: UUID) {
            mutableState.update { it.copy(downloading = reportId) }
            try {
                val file = repository.download(reportId)
                mutableState.update { it.copy(fileToOpen = file) }
            } finally {
                mutableState.update { it.copy(downloading = null) }
            }
        }

        private suspend fun waitUntilFinished(accepted: ReportJob): ReportJob {
            var job = accepted
            repeat(MAX_POLLS) {
                if (job.isFinished()) return job
                delay(POLL_INTERVAL_MS)
                job = repository.get(job.id)
            }
            return job
        }

        private companion object {
            const val POLL_INTERVAL_MS = 2_000L
            const val MAX_POLLS = 30
        }
    }

private fun ReportJob.isFinished(): Boolean = status == ReportJob.Status.DONE || status == ReportJob.Status.FAILED

/** Message for a report that is not ready: failed, or still generating after the wait. */
@StringRes
internal fun ReportJob.problemRes(): Int? =
    when (status) {
        ReportJob.Status.DONE -> null
        ReportJob.Status.FAILED -> failureRes()
        ReportJob.Status.QUEUED, ReportJob.Status.RUNNING -> R.string.report_still_generating
    }

@StringRes
internal fun ReportJob.failureRes(): Int =
    when (errorCode) {
        "REPORT_TOO_LARGE" -> R.string.report_failed_too_large
        "DEPENDENCY_UNAVAILABLE" -> R.string.report_failed_unavailable
        else -> R.string.report_failed
    }
