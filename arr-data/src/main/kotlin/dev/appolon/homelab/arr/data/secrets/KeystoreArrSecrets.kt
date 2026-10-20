package dev.appolon.homelab.arr.data.secrets

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Wraps and unwraps the random file key with a master key. Production uses the
 * AndroidKeyStore master; JVM tests substitute a software master so the file
 * handling is testable without a device.
 */
internal interface MasterKey {
    fun wrap(rawKey: ByteArray): ByteArray
    fun unwrap(wrapped: ByteArray): ByteArray
}

/** AES-256-GCM master key held in AndroidKeyStore under [alias]. */
internal class AndroidKeystoreMasterKey(
    private val alias: String = "arr_secrets",
) : MasterKey {
    private fun masterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    override fun wrap(rawKey: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        return cipher.iv + cipher.doFinal(rawKey)
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        val iv = wrapped.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = wrapped.copyOfRange(IV_LENGTH_BYTES, wrapped.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val KEY_SIZE_BITS = 256
    }
}

/**
 * Production [ArrSecrets]. A random AES-256 file key encrypts values; the
 * AndroidKeyStore master key wraps that file key, so the raw key never rests
 * on disk and wiping the Keystore invalidates everything without a
 * third-party crypto library. Key provisioning is lazy, so constructing the
 * data graph does no disk or Keystore work.
 */
class KeystoreArrSecrets internal constructor(
    keyDirectory: File,
    masterKey: MasterKey,
) : ArrSecrets {

    constructor(keyDirectory: File) : this(keyDirectory, AndroidKeystoreMasterKey())

    private val impl by lazy {
        val fileKey = SecretKeySpec(loadOrCreateFileKey(keyDirectory, masterKey), "AES")
        AesGcmArrSecrets(SoftwareGcmCrypto(fileKey))
    }

    override fun encrypt(plaintext: String): String = impl.encrypt(plaintext)

    override fun decrypt(envelope: String): String = impl.decrypt(envelope)
}

/**
 * Reads the wrapped key file, or generates a fresh random key and stores it
 * wrapped. The raw key exists only in memory.
 */
private fun loadOrCreateFileKey(keyDirectory: File, masterKey: MasterKey): ByteArray {
    keyDirectory.mkdirs()
    val keyFile = File(keyDirectory, KEY_FILE_NAME)
    if (keyFile.exists()) {
        return try {
            masterKey.unwrap(keyFile.readBytes())
        } catch (failure: GeneralSecurityException) {
            throw GeneralSecurityException(
                "arr secrets key file is unreadable (Keystore reset?); delete " +
                    "${keyFile.path} and re-enter credentials",
                failure,
            )
        }
    }
    val rawKey = ByteArray(FILE_KEY_BYTES).also { SecureRandom().nextBytes(it) }
    keyFile.writeBytes(masterKey.wrap(rawKey))
    return rawKey
}

private const val KEY_FILE_NAME = "arr_secrets_v1.key"
private const val FILE_KEY_BYTES = 32
