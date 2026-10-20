package dev.appolon.homelab.arr.data.secrets

import java.security.GeneralSecurityException
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GcmEnvelopeTest {

    private val secrets = AesGcmArrSecrets(
        SoftwareGcmCrypto(SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")),
    )

    @Test
    fun roundTripsArbitraryStrings() {
        val plaintexts = listOf(
            "a-plaintext-api-key",
            "unicode-passwörd-🔐",
            "",
            "line1\nline2",
        )
        plaintexts.forEach { plaintext ->
            assertEquals(plaintext, secrets.decrypt(secrets.encrypt(plaintext)))
        }
    }

    @Test
    fun everyEncryptionCarriesTheVersionPrefix() {
        val envelope = secrets.encrypt("value")
        assertTrue(ArrSecrets.isEncrypted(envelope))
        assertTrue(envelope.startsWith("v1:"))
        assertFalse(ArrSecrets.isEncrypted("v1-not-an-envelope-marker-check-only"))
        assertTrue(ArrSecrets.isEncrypted("v1:"))
    }

    @Test
    fun identicalPlaintextYieldsDifferentEnvelopesBecauseOfFreshIVs() {
        val first = secrets.encrypt("same")
        val second = secrets.encrypt("same")

        assertNotEquals(first, second)
        assertEquals(secrets.decrypt(first), secrets.decrypt(second))
    }

    @Test
    fun tamperedEnvelopeIsRejected() {
        val envelope = secrets.encrypt("secret")
        val payload = envelope.removePrefix(ArrSecrets.ENVELOPE_PREFIX)
        val bytes = java.util.Base64.getDecoder().decode(payload)
        // Flip a byte inside the ciphertext, after the 12-byte IV.
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0x01).toByte()
        val tampered = ArrSecrets.ENVELOPE_PREFIX +
            java.util.Base64.getEncoder().encodeToString(bytes)

        try {
            secrets.decrypt(tampered)
            throw AssertionError("expected GeneralSecurityException for tampered envelope")
        } catch (expected: GeneralSecurityException) {
            // AEADBadTagException is a GeneralSecurityException; nothing to check further.
        }
    }

    @Test
    fun wrongKeyCannotDecrypt() {
        val otherKey = AesGcmArrSecrets(
            SoftwareGcmCrypto(SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES")),
        )
        val envelope = secrets.encrypt("secret")

        try {
            otherKey.decrypt(envelope)
            throw AssertionError("expected GeneralSecurityException for wrong key")
        } catch (expected: GeneralSecurityException) {
            // Expected: authentication fails before any plaintext is exposed.
        }
    }

    @Test
    fun foreignStringsAreRejected() {
        listOf("", "plain-key", "v2:whatever", "not base64!!")
            .forEach { value ->
                try {
                    secrets.decrypt(value)
                    throw AssertionError("expected rejection for input: $value")
                } catch (expected: IllegalArgumentException) {
                    // Either the prefix check or base64 decoding rejects it.
                } catch (expected: java.security.GeneralSecurityException) {
                    // A decodable-but-truncated payload lands here instead.
                }
            }
    }
}
