package dev.inspecto.android.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.inspecto.android.BuildConfig
import dev.inspecto.android.api.infrastructure.Serializer
import dev.inspecto.android.core.auth.AuthInterceptor
import dev.inspecto.android.core.auth.TokenAuthenticator
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.overwriteWith
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Base URL of the public API, ending with a slash (for example `https://host/api/v1/`). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiBaseUrl

/** HTTP stack that sends the access token and renews it on `401`. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthorizedApi

/**
 * HTTP stack shared by the generated API interfaces (dev.inspecto.android.api.apis). Sign-in,
 * registration and token refresh use the plain stack, so renewing a token never recurses.
 */
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
            serializersModule =
                Serializer.kotlinxSerializationAdapters overwriteWith
                SerializersModule { contextual(BigDecimal::class, BigDecimalNumberSerializer) }
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
    @AuthorizedApi
    fun provideAuthorizedOkHttpClient(
        client: OkHttpClient,
        interceptor: AuthInterceptor,
        authenticator: TokenAuthenticator,
    ): OkHttpClient =
        client
            .newBuilder()
            .addInterceptor(interceptor)
            .authenticator(authenticator)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        client: OkHttpClient,
        json: Json,
        @ApiBaseUrl baseUrl: String,
    ): Retrofit = retrofit(client, json, baseUrl)

    @Provides
    @Singleton
    @AuthorizedApi
    fun provideAuthorizedRetrofit(
        @AuthorizedApi client: OkHttpClient,
        json: Json,
        @ApiBaseUrl baseUrl: String,
    ): Retrofit = retrofit(client, json, baseUrl)

    /** Retrofit for the given client with the converters of the API. */
    fun retrofit(
        client: OkHttpClient,
        json: Json,
        baseUrl: String,
    ): Retrofit =
        Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(EnumValueConverterFactory())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
}
