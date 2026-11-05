package dev.inspecto.android.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.inspecto.android.api.apis.AuthApi
import dev.inspecto.android.api.apis.PropertiesApi
import dev.inspecto.android.api.apis.RoomsApi
import retrofit2.Retrofit
import javax.inject.Singleton

/** Generated API interfaces bound to the HTTP stacks of [NetworkModule]. */
@Module
@InstallIn(SingletonComponent::class)
object ApiModule {
    /** Sign-in, registration and token refresh: no access token is sent or renewed. */
    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    /** Operations of [AuthApi] that need the access token (sign-out). */
    @Provides
    @Singleton
    @AuthorizedApi
    fun provideAuthorizedAuthApi(
        @AuthorizedApi retrofit: Retrofit,
    ): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun providePropertiesApi(
        @AuthorizedApi retrofit: Retrofit,
    ): PropertiesApi = retrofit.create(PropertiesApi::class.java)

    @Provides
    @Singleton
    fun provideRoomsApi(
        @AuthorizedApi retrofit: Retrofit,
    ): RoomsApi = retrofit.create(RoomsApi::class.java)
}
