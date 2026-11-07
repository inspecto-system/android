package dev.inspecto.android.feature.reports

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.inspecto.android.BuildConfig
import dev.inspecto.android.core.network.AuthorizedApi
import dev.inspecto.android.core.network.NetworkModule
import dev.inspecto.android.report.api.apis.ReportsApi
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

/** Retrofit of report-service. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ReportService

/** report-service with the same access token as core-service, like media-service. */
@Module
@InstallIn(SingletonComponent::class)
object ReportModule {
    @Provides
    @Singleton
    @ReportService
    fun provideReportRetrofit(
        @AuthorizedApi client: OkHttpClient,
        json: Json,
    ): Retrofit = NetworkModule.retrofit(client, json, BuildConfig.REPORT_BASE_URL)

    @Provides
    @Singleton
    fun provideReportsApi(
        @ReportService retrofit: Retrofit,
    ): ReportsApi = retrofit.create(ReportsApi::class.java)

    @Provides
    @Singleton
    fun provideReportFileApi(
        @ReportService retrofit: Retrofit,
    ): ReportFileApi = retrofit.create(ReportFileApi::class.java)

    @Provides
    @ReportDirectory
    fun provideReportDirectory(
        @ApplicationContext context: Context,
    ): File = File(context.cacheDir, "reports")
}
