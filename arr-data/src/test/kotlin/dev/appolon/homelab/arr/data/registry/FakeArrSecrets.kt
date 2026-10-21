package dev.appolon.homelab.arr.data.registry

import dev.appolon.homelab.arr.data.secrets.ArrSecrets

/**
 * Deterministic [ArrSecrets] for tests. Envelopes still carry the shared
 * version marker so idempotence checks behave like production.
 */
internal class FakeArrSecrets : ArrSecrets {
    val encryptedPlaintexts = mutableListOf<String>()

    override fun encrypt(plaintext: String): String =
        "$ENVELOPE_MARKER$plaintext".also { encryptedPlaintexts.add(plaintext) }

    override fun decrypt(envelope: String): String =
        envelope.removePrefix(ENVELOPE_MARKER)

    companion object {
        const val ENVELOPE_MARKER = "${ArrSecrets.ENVELOPE_PREFIX}fake:"
    }
}
