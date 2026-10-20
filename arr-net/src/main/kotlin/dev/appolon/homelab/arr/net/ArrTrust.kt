package dev.appolon.homelab.arr.net

import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

/**
 * Turns a [TrustSpec] into client TLS configuration. `System` leaves the JVM
 * defaults untouched. `AcceptAny` and `Pinned` install an [SSLContext] built
 * around a per-server [X509TrustManager]. Only `Pinned` relaxes hostname
 * verification, because there the pin itself is the identity check and
 * self-signed certs routinely carry an unrelated hostname.
 */
internal fun OkHttpClient.Builder.configureTrust(spec: TrustSpec): OkHttpClient.Builder =
    when (spec) {
    TrustSpec.System -> this

    TrustSpec.AcceptAny -> {
        val trustManager = AcceptAllTrustManager
        sslSocketFactory(sslContextFor(trustManager), trustManager)
        this
    }

    is TrustSpec.Pinned -> {
        val trustManager = PinnedTrustManager(spec.sha256FingerprintHex)
        sslSocketFactory(sslContextFor(trustManager), trustManager)
        hostnameVerifier { _, _ -> true }
        this
    }
}

private fun sslContextFor(trustManager: X509TrustManager): javax.net.ssl.SSLSocketFactory =
    SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(trustManager), null)
    }.socketFactory

/** Accepts any server certificate. Only reachable through explicit user opt-in. */
private object AcceptAllTrustManager : X509TrustManager {
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

/**
 * Trusts exactly one leaf certificate, identified by the SHA-256 fingerprint of
 * its DER encoding. Fingerprints are compared after stripping separators
 * (`:` or spaces) and lowercasing, so values pasted from a browser or openssl
 * output work unmodified.
 */
private class PinnedTrustManager(fingerprintHex: String) : X509TrustManager {
    private val expected = normalizeFingerprint(fingerprintHex)

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        throw CertificateException("Client certificates are not supported for arr servers")
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        val leaf = chain.firstOrNull()
            ?: throw CertificateException("Server sent an empty certificate chain")
        if (sha256Hex(leaf.encoded) != expected) {
            throw CertificateException(
                "Server certificate fingerprint does not match the configured pin",
            )
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

internal fun normalizeFingerprint(hex: String): String =
    hex.filter { it.isLetterOrDigit() }.lowercase()

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
