package com.doctorknepper.personalore.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * How PersonaLore protects your journal on disk
 * ---------------------------------------------
 * Every file (entries, prompt progress, photos, voice notes) is encrypted with one random
 * 256-bit AES-GCM "data key". The data key itself is never stored in the clear. It is stored
 * "wrapped" (encrypted) in up to four ways:
 *
 *  - No app lock:   wrapped by a key held in the Android Keystore (hardware-backed on most phones).
 *  - PIN:           wrapped by the Keystore key AND then by a key derived from your PIN, so a copy
 *                   of the app's files can't be brute-forced off the phone.
 *  - Recovery key:  wrapped by a key derived from a long random recovery key you write down.
 *                   Works even if the Keystore key is ever lost.
 *  - Fingerprint:   wrapped by a Keystore key that only works after a strong biometric check.
 *
 * When the lock is on, the no-lock wrapping is deleted, so the PIN, recovery key or fingerprint
 * is required to read anything.
 */
object Crypto {
    private val rng = SecureRandom()
    private const val MAGIC = "PLE1"
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    const val PBKDF2_ITERATIONS = 150_000

    fun randomBytes(n: Int) = ByteArray(n).also { rng.nextBytes(it) }

    fun newDataKey(): SecretKey = SecretKeySpec(randomBytes(32), "AES")

    fun keyFrom(bytes: ByteArray): SecretKey = SecretKeySpec(bytes, "AES")

    /** MAGIC | IV | ciphertext+tag */
    fun encrypt(key: SecretKey, plain: ByteArray): ByteArray {
        val iv = randomBytes(IV_LEN)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return MAGIC.toByteArray() + iv + c.doFinal(plain)
    }

    fun decrypt(key: SecretKey, data: ByteArray): ByteArray {
        require(isEncrypted(data)) { "Not an encrypted PersonaLore file" }
        val iv = data.copyOfRange(4, 4 + IV_LEN)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return c.doFinal(data, 4 + IV_LEN, data.size - 4 - IV_LEN)
    }

    fun isEncrypted(data: ByteArray) = data.size > 4 + IV_LEN && data.copyOfRange(0, 4).contentEquals(MAGIC.toByteArray())

    fun deriveKey(secret: String, salt: ByteArray, iterations: Int = PBKDF2_ITERATIONS): SecretKey {
        val spec = PBEKeySpec(secret.toCharArray(), salt, iterations, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return keyFrom(bytes)
    }

    /** salt(16) | encrypt(derived, payload) */
    fun wrapWithSecret(secret: String, payload: ByteArray): ByteArray {
        val salt = randomBytes(16)
        return salt + encrypt(deriveKey(secret, salt), payload)
    }

    /** Throws if the secret is wrong (GCM authentication fails). */
    fun unwrapWithSecret(secret: String, wrapped: ByteArray): ByteArray =
        decrypt(deriveKey(secret, wrapped.copyOfRange(0, 16)), wrapped.copyOfRange(16, wrapped.size))

    // ---------- recovery keys ----------

    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ" // Crockford base32, no I L O U

    /** 20 characters = 100 bits, shown as XXXXX-XXXXX-XXXXX-XXXXX. */
    fun newRecoveryKey(): String {
        val chars = (1..20).map { ALPHABET[rng.nextInt(32)] }.joinToString("")
        return chars.chunked(5).joinToString("-")
    }

    /** Forgiving input: case, spaces, dashes and look-alike letters don't matter. */
    fun normalizeRecoveryKey(input: String): String {
        val cleaned = input.uppercase().filter { it.isLetterOrDigit() }
            .replace('O', '0').replace('I', '1').replace('L', '1')
        return cleaned.chunked(5).joinToString("-")
    }
}

/** Wraps small secrets with a Keystore key. Swappable so tests can run without a real Keystore. */
interface KeyWrapper {
    fun wrap(plain: ByteArray): ByteArray
    fun unwrap(wrapped: ByteArray): ByteArray
    fun reset()
}

class AndroidKeystoreWrapper(private val alias: String = "personalore_wrap") : KeyWrapper {
    private val ks: KeyStore get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(): SecretKey {
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    override fun wrap(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return c.iv + c.doFinal(plain)
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, wrapped.copyOfRange(0, 12)))
        return c.doFinal(wrapped, 12, wrapped.size - 12)
    }

    override fun reset() { runCatching { ks.deleteEntry(alias) } }
}

/** Keystore key that only works right after a strong biometric check (used with BiometricPrompt.CryptoObject). */
object BiometricKey {
    private const val ALIAS = "personalore_bio"
    private val ks: KeyStore get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(create: Boolean): SecretKey? {
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        if (!create) return null
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build(),
        )
        return gen.generateKey()
    }

    fun encryptCipher(): Cipher {
        delete()
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(create = true)) }
    }

    /** Null if there's no key, or it was invalidated (e.g. a new fingerprint was enrolled). */
    fun decryptCipher(iv: ByteArray): Cipher? = runCatching {
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(128, iv)) }
    }.getOrNull()

    fun delete() { runCatching { ks.deleteEntry(ALIAS) } }
}
