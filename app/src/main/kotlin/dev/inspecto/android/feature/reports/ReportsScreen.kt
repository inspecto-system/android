package dev.inspecto.android.feature.reports

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.inspecto.android.R
import dev.inspecto.android.feature.inspections.localDate
import dev.inspecto.android.report.api.models.ReportJob
import dev.inspecto.android.ui.components.ErrorText
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state.fileToOpen) {
        state.fileToOpen?.let { file ->
            if (openPdf(context, file)) viewModel.opened() else viewModel.noViewer()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reports_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
                actions = {
                    TextButton(onClick = viewModel::reload, enabled = !state.loading) {
                        Text(stringResource(R.string.reports_refresh))
                    }
                },
            )
        },
    ) { padding ->
        ReportsContent(state, viewModel::order, viewModel::open, Modifier.padding(padding))
    }
}

@Composable
internal fun ReportsContent(
    state: ReportsState,
    onOrder: () -> Unit,
    onOpen: (ReportJob) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Button(onClick = onOrder, enabled = !state.generating, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.report_order))
            }
        }
        if (state.generating) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(stringResource(R.string.report_generating))
                }
            }
        }
        item { ErrorText(state.errorRes) }
        when {
            state.loading && state.reports.isEmpty() -> item { CircularProgressIndicator() }
            state.reports.isEmpty() -> item { Text(stringResource(R.string.reports_empty)) }
            else ->
                items(
                    state.reports,
                    key = { it.id },
                ) { report -> ReportCard(report, state.downloading == report.id, onOpen) }
        }
    }
}

@Composable
private fun ReportCard(
    report: ReportJob,
    downloading: Boolean,
    onOpen: (ReportJob) -> Unit,
) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(
                    R.string.report_created,
                    report.createdAt.localDate(),
                    timeFormat.format(report.createdAt.atZoneSameInstant(ZoneId.systemDefault())),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(stringResource(report.statusRes()))
            if (report.status == ReportJob.Status.DONE) {
                val pages = report.pageCount
                val size = report.sizeBytes
                if (pages != null && size != null) {
                    Text(stringResource(R.string.report_details, pages, Formatter.formatShortFileSize(context, size)))
                }
                TextButton(onClick = { onOpen(report) }, enabled = !downloading) {
                    Text(stringResource(if (downloading) R.string.report_downloading else R.string.report_open))
                }
            }
            if (report.status == ReportJob.Status.FAILED) {
                ErrorText(report.failureRes())
            }
        }
    }
}

private fun ReportJob.statusRes(): Int =
    when (status) {
        ReportJob.Status.QUEUED -> R.string.report_status_queued
        ReportJob.Status.RUNNING -> R.string.report_status_running
        ReportJob.Status.DONE -> R.string.report_status_done
        ReportJob.Status.FAILED -> R.string.report_status_failed
    }

/** Opens the PDF in a viewer app through the app's FileProvider; false when no app can show it. */
private fun openPdf(
    context: Context,
    file: File,
): Boolean {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (ignored: ActivityNotFoundException) {
        false
    }
}
