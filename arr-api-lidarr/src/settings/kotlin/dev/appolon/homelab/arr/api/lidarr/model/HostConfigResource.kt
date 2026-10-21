package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/**
 * Lidarr HostConfigResource wire resource for the general and security host
 * settings. Shared enums live beside this file; SystemResource reuses
 * AuthenticationType and UpdateMechanism.
 */
@Serializable
data class HostConfigResource(
    val id: Int = 0,
    val bindAddress: String? = null,
    val port: Int = 0,
    val sslPort: Int = 0,
    val enableSsl: Boolean = false,
    val launchBrowser: Boolean = false,
    val authenticationMethod: AuthenticationType = AuthenticationType.NONE,
    val authenticationRequired: AuthenticationRequiredType = AuthenticationRequiredType.ENABLED,
    val analyticsEnabled: Boolean = false,
    val username: String? = null,
    val password: String? = null,
    val passwordConfirmation: String? = null,
    val logLevel: String? = null,
    val logSizeLimit: Int = 0,
    val consoleLogLevel: String? = null,
    val branch: String? = null,
    val apiKey: String? = null,
    val sslCertPath: String? = null,
    val sslCertPassword: String? = null,
    val urlBase: String? = null,
    val instanceName: String? = null,
    val applicationUrl: String? = null,
    val updateAutomatically: Boolean = false,
    val updateMechanism: UpdateMechanism = UpdateMechanism.BUILT_IN,
    val updateScriptPath: String? = null,
    val proxyEnabled: Boolean = false,
    val proxyType: ProxyType = ProxyType.HTTP,
    val proxyHostname: String? = null,
    val proxyPort: Int = 0,
    val proxyUsername: String? = null,
    val proxyPassword: String? = null,
    val proxyBypassFilter: String? = null,
    val proxyBypassLocalAddresses: Boolean = false,
    val certificateValidation: CertificateValidationType = CertificateValidationType.ENABLED,
    val backupFolder: String? = null,
    val backupInterval: Int = 0,
    val backupRetention: Int = 0,
    val trustCgnatIpAddresses: Boolean = false,
)
