package dev.appolon.homelab.arr.data.secrets

import java.io.File
import java.nio.file.Files
import java.security.GeneralSecurityException
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the key-file design with a software master key: a fresh random
 * file key is generated, persisted only in wrapped form, and reused by later
 * instances. No Android Keystore involved.
 */
class KeystoreArrSecretsTest {

    @Test
    fun keyFileIsCreatedWrappedAndReusedAcrossInstances() {
        val directory = tempDirectory()
        val master = FakeMasterKey()

        val first = KeystoreArrSecrets(directory, master)
        val envelope = first.encrypt("api-key")
        assertTrue(master.wrapCount >= 1)

        // A later instance (new process, same Keystore master) decrypts it.
        val second = KeystoreArrSecrets(directory, FakeMasterKey())
        assertEquals("api-key", second.decrypt(envelope))
        // Key provisioning happened exactly once: the second instance read
        // the existing file instead of generating another key.
        assertEquals(1, master.wrapCount)
    }

    @Test
    fun rawKeyNeverRestsOnDisk() {
        val directory = tempDirectory()
        val master = FakeMasterKey()

        val secrets = KeystoreArrSecrets(directory, master)
        secrets.encrypt("api-key")

        val keyFile = File(directory, "arr_secrets_v1.key")
        assertTrue(keyFile.exists())
        val onDisk = keyFile.readBytes()
        // Wrapped form: 12-byte IV + 32-byte key + 16-byte GCM tag.
        assertEquals(60, onDisk.size)
        // The file holds exactly what the master wrapped, never the raw key.
        org.junit.Assert.assertArrayEquals(master.lastWrapped, onDisk)
    }

    @Test
    fun aDifferentMasterKeyCannotUnlockTheFile() {
        val directory = tempDirectory()
        KeystoreArrSecrets(directory, FakeMasterKey()).encrypt("api-key")

        val stranger = KeystoreArrSecrets(directory, OtherMasterKey())
        try {
            stranger.decrypt("v1:anything-at-all")
            throw AssertionError("expected GeneralSecurityException")
        } catch (expected: GeneralSecurityException) {
            // The wrapped file key fails to authenticate under a foreign master.
        }
    }

    @Test
    fun masterLossSurfacesAsGeneralSecurityExceptionWithRecoveryHint() {
        val directory = tempDirectory()
        KeystoreArrSecrets(directory, FakeMasterKey()).encrypt("api-key")

        val orphaned = KeystoreArrSecrets(directory, FakeMasterKey(failUnwrap = true))
        try {
            orphaned.encrypt("api-key")
            throw AssertionError("expected GeneralSecurityException")
        } catch (expected: GeneralSecurityException) {
            assertTrue(expected.message!!.contains("re-enter credentials"))
        }
    }

    private fun tempDirectory(): File = Files.createTempDirectory("arr-secrets-test").toFile()

    /** Software stand-in for the AndroidKeyStore master. */
    private class FakeMasterKey(
        private val failUnwrap: Boolean = false,
    ) : MasterKey {
        var wrapCount = 0
            private set
        var lastWrapped: ByteArray = ByteArray(0)
            private set

        private val crypto = SoftwareGcmCrypto(masterKeyBytes())

        override fun wrap(rawKey: ByteArray): ByteArray {
            wrapCount++
            return crypto.seal(rawKey).also { lastWrapped = it }
        }

        override fun unwrap(wrapped: ByteArray): ByteArray {
            if (failUnwrap) throw GeneralSecurityException("keystore wiped")
            return crypto.open(wrapped)
        }
    }

    /** A different master entirely, to prove the file is not self-decrypting. */
    private class OtherMasterKey : MasterKey {
        private val crypto = SoftwareGcmCrypto(SecretKeySpec(ByteArray(32) { 77 }, "AES"))
        override fun wrap(rawKey: ByteArray): ByteArray = crypto.seal(rawKey)
        override fun unwrap(wrapped: ByteArray): ByteArray = crypto.open(wrapped)
    }

    private companion object {
        fun masterKeyBytes() = SecretKeySpec(ByteArray(32) { 9 }, "AES")
    }
}
