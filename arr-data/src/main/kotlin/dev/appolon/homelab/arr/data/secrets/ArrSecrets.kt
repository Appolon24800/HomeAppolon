package dev.appolon.homelab.arr.data.secrets

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts and decrypts sensitive string fields such as API keys. */
interface ArrSecrets {
    /** Returns an opaque envelope string that is safe to persist. */
    fun encrypt(plaintext: String): String

    /**
     * Reverses [encrypt]. Throws [java.security.GeneralSecurityException] when
     * the envelope is tampered with or came from a different key.
     */
    fun decrypt(envelope: String): String

    companion object {
        /** Envelope version marker shared by every implementation. */
        const val ENVELOPE_PREFIX = "v1:"

        /** True when [value] is already an [ArrSecrets] envelope. */
        fun isEncrypted(value: String): Boolean = value.startsWith(ENVELOPE_PREFIX)
    }
}

/**
 * Raw AES-GCM primitive. Kept as its own seam so envelope and IV handling are
 * exercised in JVM tests with a software key while production runs the same
 * code with the key-file key.
 */
internal interface GcmCrypto {
    /** Returns the random IV (12 bytes) followed by ciphertext and GCM tag. */
    fun seal(plaintext: ByteArray): ByteArray

    /** Reverses [seal]; throws on tamper, wrong key, or truncated input. */
    fun open(ivAndCiphertext: ByteArray): ByteArray
}

/** Software AES-256-GCM over any [SecretKey]; no Keystore involved. */
internal class SoftwareGcmCrypto(private val key: SecretKey) : GcmCrypto {
    override fun seal(plaintext: ByteArray): ByteArray {
        val iv = ByteArray(IV_LENGTH_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return iv + cipher.doFinal(plaintext)
    }

    override fun open(ivAndCiphertext: ByteArray): ByteArray {
        require(ivAndCiphertext.size > IV_LENGTH_BYTES) { "payload is shorter than an IV" }
        val iv = ivAndCiphertext.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = ivAndCiphertext.copyOfRange(IV_LENGTH_BYTES, ivAndCiphertext.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH_BYTES = 12
        const val GCM_TAG_BITS = 128
        val random = SecureRandom()
    }
}

/**
 * Envelope format for every encrypted value: "v1:" followed by base64 of
 * (iv || ciphertext). One place owns the format so all implementations write
 * and read identical envelopes, and the version marker leaves room for a
 * future key or algorithm change.
 */
internal class GcmEnvelope(private val crypto: GcmCrypto) {
    fun seal(plaintext: String): String =
        ArrSecrets.ENVELOPE_PREFIX +
            Base64.getEncoder().encodeToString(crypto.seal(plaintext.toByteArray(Charsets.UTF_8)))

    fun open(envelope: String): String {
        require(ArrSecrets.isEncrypted(envelope)) { "value is not an arr secrets envelope" }
        val payload = Base64.getDecoder()
            .decode(envelope.substring(ArrSecrets.ENVELOPE_PREFIX.length))
        return String(crypto.open(payload), Charsets.UTF_8)
    }
}

/** [ArrSecrets] over any [GcmCrypto]; tests instantiate this directly. */
internal class AesGcmArrSecrets(crypto: GcmCrypto) : ArrSecrets {
    private val codec = GcmEnvelope(crypto)

    override fun encrypt(plaintext: String): String = codec.seal(plaintext)

    override fun decrypt(envelope: String): String = codec.open(envelope)
}
