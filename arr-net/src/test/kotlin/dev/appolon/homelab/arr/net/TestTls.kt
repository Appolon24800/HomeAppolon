package dev.appolon.homelab.arr.net

import java.io.InputStream
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import javax.net.ssl.TrustManagerFactory

/**
 * Loads the test-only self-signed certificates from src/test/resources/tls so
 * TLS behavior can be exercised without adding okhttp-tls to the catalog.
 * `localhost` covers the server's own name; `mismatch` carries SAN
 * DNS:arr.test to prove pinned connections bypass hostname verification.
 */
object TestTls {
    val localhost: Material = Material("/tls/localhost-cert.pem", "/tls/localhost-key-pkcs8.pem")
    val mismatch: Material = Material("/tls/mismatch-cert.pem", "/tls/mismatch-key-pkcs8.pem")

    class Material(certResource: String, keyResource: String) {
        val certificate: X509Certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(resource(certResource)) as X509Certificate

        val privateKey: PrivateKey = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(decodePem(resource(keyResource).readBytes())))

        private val keyStore = java.security.KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null)
            setKeyEntry("test", privateKey, CharArray(0), arrayOf(certificate))
        }

        val sslContext: SSLContext = SSLContext.getInstance("TLS").apply {
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(keyStore, CharArray(0))
            init(kmf.keyManagers, null, null)
        }

        /** SHA-256 of the DER encoding, lowercase hex, no separators. */
        val sha256FingerprintHex: String = sha256Hex(certificate.encoded)
    }

    /** The default JVM trust manager, for asserting that System really means system. */
    val platformTrustManager: X509TrustManager = TrustManagerFactory
        .getInstance(TrustManagerFactory.getDefaultAlgorithm())
        .apply { init(null as java.security.KeyStore?) }
        .trustManagers
        .filterIsInstance<X509TrustManager>()
        .first()

    private fun resource(path: String): InputStream =
        TestTls::class.java.getResourceAsStream(path)
            ?: error("Missing test resource $path")

    private fun decodePem(bytes: ByteArray): ByteArray {
        val text = bytes.decodeToString()
        val body = text.lineSequence()
            .filterNot { it.startsWith("-----") }
            .joinToString("")
        return Base64.getDecoder().decode(body)
    }
}
