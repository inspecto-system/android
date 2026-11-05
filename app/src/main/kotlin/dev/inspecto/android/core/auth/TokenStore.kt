package dev.inspecto.android.core.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the refresh token across app restarts, encrypted with [TokenCipher] (ADR-008). The access
 * token lives only in memory ([SessionRepository]) and is renewed after a restart.
 */
@Singleton
class TokenStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
        private val cipher: TokenCipher,
    ) {
        /** Stored refresh token, or `null` when there is none or it cannot be decrypted. */
        suspend fun refreshToken(): String? {
            val encrypted = dataStore.data.first()[REFRESH_TOKEN] ?: return null
            return try {
                cipher.decrypt(encrypted)
            } catch (ignored: GeneralSecurityException) {
                // The Keystore key was removed (for example, the screen lock was reset).
                clear()
                null
            }
        }

        suspend fun saveRefreshToken(token: String) {
            val encrypted = cipher.encrypt(token)
            dataStore.edit { it[REFRESH_TOKEN] = encrypted }
        }

        suspend fun clear() {
            dataStore.edit { it.remove(REFRESH_TOKEN) }
        }

        private companion object {
            val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        }
    }
