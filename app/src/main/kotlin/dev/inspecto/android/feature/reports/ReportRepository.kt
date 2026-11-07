package dev.inspecto.android.feature.reports

import dev.inspecto.android.core.error.AppError
import dev.inspecto.android.core.error.apiCall
import dev.inspecto.android.feature.inspections.apiLanguage
import dev.inspecto.android.report.api.apis.ReportsApi
import dev.inspecto.android.report.api.models.CreateReportRequest
import dev.inspecto.android.report.api.models.ReportJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Streaming
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Qualifier

/**
 * Download of a report file. The generated client reads `GET /reports/{reportId}/file` as text, which
 * corrupts a PDF, so this one operation of platform/api/report.yaml is declared by hand.
 */
interface ReportFileApi {
    @Streaming
    @GET("reports/{reportId}/file")
    suspend fun download(
        @Path("reportId") reportId: UUID,
    ): Response<ResponseBody>
}

/** Directory of downloaded reports in the app's cache: never backed up, may be cleared by the system. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ReportDirectory

/** PDF reports of inspections (platform/api/report.yaml, tag reports). */
class ReportRepository
    @Inject
    constructor(
        private val reports: ReportsApi,
        private val files: ReportFileApi,
        @ReportDirectory private val directory: File,
    ) {
        /** Reports of the inspection, newest first. */
        suspend fun list(inspectionId: UUID): List<ReportJob> = apiCall { reports.listReports(inspectionId) }

        /**
         * Orders a report in the language of the device. Repeating the call with the same [idempotencyKey]
         * (for example after a lost answer) returns the job of the first call.
         */
        suspend fun request(
            inspectionId: UUID,
            idempotencyKey: String,
        ): ReportJob {
            val locale = if (apiLanguage() == "EN") CreateReportRequest.Locale.EN else CreateReportRequest.Locale.RU
            return apiCall { reports.requestReport(CreateReportRequest(inspectionId, locale), idempotencyKey) }
        }

        suspend fun get(reportId: UUID): ReportJob = apiCall { reports.getReport(reportId) }

        /** The PDF on the device, downloaded once: a report never changes after it is generated. */
        suspend fun download(reportId: UUID): File {
            val file = File(directory, "$reportId.pdf")
            if (file.isFile) return file
            val body = apiCall { files.download(reportId) }
            return withContext(Dispatchers.IO) {
                val partial = File(directory, "$reportId.part")
                try {
                    directory.mkdirs()
                    body.use { partial.outputStream().use { out -> it.byteStream().copyTo(out) } }
                    if (!partial.renameTo(file)) throw IOException("Cannot store report $reportId")
                    file
                } catch (e: IOException) {
                    partial.delete()
                    throw AppError.Network(e)
                }
            }
        }
    }
