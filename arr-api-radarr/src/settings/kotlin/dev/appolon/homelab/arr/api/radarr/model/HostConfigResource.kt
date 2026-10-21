package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/**
 * Wire contract for Radarr HostConfigResource. Dates remain unparsed strings.
 * Secret-bearing fields (password, apiKey, proxy credentials) round-trip as opaque
 * strings; the app never logs or persists them outside the encrypted registry.
 */
@Serializable
data class HostConfigResource(
    val id: Int = 0,
    val bindAddress: String? = null,
    val port: Int = 0,
    val sslPort: Int = 0,
    val enableSsl: Boolean = false,
    val launchBrowser: Boolean = false,
    val authenticationMethod: AuthenticationType? = null,
    val authenticationRequired: AuthenticationRequiredType? = null,
    val allowedHosts: String? = null,
    val analyticsEnabled: Boolean = false,
    val username: String? = null,
    val password: String? = null,
    val passwordConfirmation: String? = null,
    val oidcAuthority: String? = null,
    val oidcClientId: String? = null,
    val oidcClientSecret: String? = null,
    val oidcUserIdentifier: String? = null,
    val oidcScopes: String? = null,
    val logLevel: String? = null,
    val logSizeLimit: Int = 0,
    val consoleLogLevel: String? = null,
    val branch: String? = null,
    val apiKey: String? = null,
    val sslCertPath: String? = null,
    val sslKeyPath: String? = null,
    val sslCertPassword: String? = null,
    val urlBase: String? = null,
    val trustedNetworks: String? = null,
    val instanceName: String? = null,
    val applicationUrl: String? = null,
    val updateAutomatically: Boolean = false,
    val updateMechanism: UpdateMechanism? = null,
    val updateScriptPath: String? = null,
    val proxyEnabled: Boolean = false,
    val proxyType: ProxyType? = null,
    val proxyHostname: String? = null,
    val proxyPort: Int = 0,
    val proxyUsername: String? = null,
    val proxyPassword: String? = null,
    val proxyBypassFilter: String? = null,
    val proxyBypassLocalAddresses: Boolean = false,
    val certificateValidation: CertificateValidationType? = null,
    val backupFolder: String? = null,
    val backupInterval: Int = 0,
    val backupRetention: Int = 0,
    val trustCgnatIpAddresses: Boolean = false,
)
