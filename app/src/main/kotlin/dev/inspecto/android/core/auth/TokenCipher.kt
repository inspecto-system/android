package dev.inspecto.android.core.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

/** Encrypts secrets before they are written to storage. */
interface TokenCipher {
    fun encrypt(plain: String): String

    fun decrypt(encrypted: String): String
}

/**
 * AES-256-GCM with a key that never leaves the Android Keystore (SECURITY.md §10). The output is
 * Base64 of the 12-byte IV followed by the ciphertext and tag.
 */
class KeystoreTokenCipher
    @Inject
    constructor() : TokenCipher {
        override fun encrypt(plain: String): String {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val encrypted = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            return Base64.encodeToString(encrypted, Base64.NO_WRAP)
        }

        override fun decrypt(encrypted: String): String {
            val bytes = Base64.decode(encrypted, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES)
            cipher.init(Cipher.DECRYPT_MODE, key(), iv)
            val plain = cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES)
            return String(plain, Charsets.UTF_8)
        }

        private fun key(): SecretKey {
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            generator.init(
                KeyGenParameterSpec
                    .Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_BITS)
                    .build(),
            )
            return generator.generateKey()
        }

        private companion object {
            const val KEYSTORE = "AndroidKeyStore"
            const val ALIAS = "inspecto-session"
            const val TRANSFORMATION = "AES/GCM/NoPadding"
            const val KEY_BITS = 256
            const val TAG_BITS = 128
            const val IV_BYTES = 12
        }
    }
