package dev.inspecto.android.core.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Android Keystore and file DataStore exist only on a device: run with `connectedDebugAndroidTest`. */
@RunWith(AndroidJUnit4::class)
class TokenStoreDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = File(context.filesDir, "datastore/test-session.preferences_pb")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun cleanUp() {
        scope.cancel()
        file.delete()
    }

    @Test
    fun keystoreCipherRoundTripsAndUsesFreshIv() {
        val cipher = KeystoreTokenCipher()

        val first = cipher.encrypt("refresh-token")
        val second = cipher.encrypt("refresh-token")

        assertNotEquals(first, second)
        assertFalse(first.contains("refresh-token"))
        assertEquals("refresh-token", cipher.decrypt(first))
    }

    @Test
    fun storeKeepsOnlyEncryptedTokenOnDisk() =
        runTest {
            val store = TokenStore(PreferenceDataStoreFactory.create(scope = scope) { file }, KeystoreTokenCipher())

            store.saveRefreshToken("refresh-token")

            assertEquals("refresh-token", store.refreshToken())
            assertFalse(String(file.readBytes()).contains("refresh-token"))
            store.clear()
            assertNull(store.refreshToken())
        }
}
