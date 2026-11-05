package dev.inspecto.android.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.inspecto.android.BuildConfig
import dev.inspecto.android.api.infrastructure.Serializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Base URL of the public API, ending with a slash (for example {@code https://host/api/v1/}). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiBaseUrl

/** HTTP stack shared by the generated API interfaces (dev.inspecto.android.api.apis). */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private const val TIMEOUT_SECONDS = 20L

    @Provides
    @ApiBaseUrl
    fun provideApiBaseUrl(): String = BuildConfig.API_BASE_URL

    /**
     * JSON of the API: unknown fields are ignored so that the app keeps working when the server
     * adds optional fields (backward-compatible contract changes, api/README.md in platform).
     */
    @Provides
    @Singleton
    fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            serializersModule = Serializer.kotlinxSerializationAdapters
        }

    /** HTTP client; request and response bodies are never logged (SECURITY.md §10). */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        client: OkHttpClient,
        json: Json,
        @ApiBaseUrl baseUrl: String,
    ): Retrofit =
        Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(EnumValueConverterFactory())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
}
