package dev.inspecto.android.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.inspecto.android.api.apis.SuggestionsApi
import retrofit2.Retrofit
import javax.inject.Singleton

/** Category suggestions of core-service (T-602), with the user's access token. */
@Module
@InstallIn(SingletonComponent::class)
object SuggestionsApiModule {
    @Provides
    @Singleton
    fun provideSuggestionsApi(
        @AuthorizedApi retrofit: Retrofit,
    ): SuggestionsApi = retrofit.create(SuggestionsApi::class.java)
}
