package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How the host authenticates API and UI callers, from the spec's
 * AuthenticationType enum. NONE means the API key header is still accepted.
 */
@Serializable
enum class AuthenticationType {
    @SerialName("none") NONE,
    @SerialName("basic") BASIC,
    @SerialName("forms") FORMS,
    @SerialName("external") EXTERNAL,
}

/** When credentials are demanded, from the spec's AuthenticationRequiredType enum. */
@Serializable
enum class AuthenticationRequiredType {
    @SerialName("enabled") ENABLED,
    @SerialName("disabledForLocalAddresses") DISABLED_FOR_LOCAL_ADDRESSES,
}

/** How the server installs updates, from the spec's UpdateMechanism enum. */
@Serializable
enum class UpdateMechanism {
    @SerialName("builtIn") BUILT_IN,
    @SerialName("script") SCRIPT,
    @SerialName("external") EXTERNAL,
    @SerialName("apt") APT,
    @SerialName("docker") DOCKER,
}

/** Outbound proxy protocol, from the spec's ProxyType enum. */
@Serializable
enum class ProxyType {
    @SerialName("http") HTTP,
    @SerialName("socks4") SOCKS_4,
    @SerialName("socks5") SOCKS_5,
}

/** TLS certificate checking for outbound connections, from the spec's enum. */
@Serializable
enum class CertificateValidationType {
    @SerialName("enabled") ENABLED,
    @SerialName("disabledForLocalAddresses") DISABLED_FOR_LOCAL_ADDRESSES,
    @SerialName("disabled") DISABLED,
}

/**
 * Settings singleton behind the HostConfig tag: bind address, authentication
 * credentials, logging, updates, proxy and backup policy. The server always
 * serves the row with id 1. [password], [passwordConfirmation],
 * [proxyPassword] and [apiKey] are secrets; never log or render them in
 * plain text.
 */
@Serializable
data class HostConfigResource(
    val id: Int = 1,
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
    val historyCleanupDays: Int = 0,
    val trustCgnatIpAddresses: Boolean = false,
)
