package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr SystemResource. Dates remain unparsed strings.
 * The UI parses timestamps with `:arr-core` ArrTime. */
@Serializable
data class SystemResource(
    val appName: String? = null,
    val instanceName: String? = null,
    val version: String? = null,
    val buildTime: String = "",
    val isDebug: Boolean = false,
    val isProduction: Boolean = false,
    val isAdmin: Boolean = false,
    val isUserInteractive: Boolean = false,
    val startupPath: String? = null,
    val appData: String? = null,
    val osName: String? = null,
    val osVersion: String? = null,
    val isNetCore: Boolean = false,
    val isLinux: Boolean = false,
    val isOsx: Boolean = false,
    val isWindows: Boolean = false,
    val isDocker: Boolean = false,
    val isContainerized: Boolean = false,
    val mode: RuntimeMode? = null,
    val branch: String? = null,
    val databaseType: DatabaseType? = null,
    val databaseVersion: String? = null,
    val authentication: AuthenticationType? = null,
    val migrationVersion: Int = 0,
    val urlBase: String? = null,
    val runtimeVersion: String? = null,
    val runtimeName: String? = null,
    val startTime: String = "",
    val packageVersion: String? = null,
    val packageAuthor: String? = null,
    val packageUpdateMechanism: UpdateMechanism? = null,
    val packageUpdateMechanismMessage: String? = null,
)
