package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.Serializable

/**
 * Server self-description from `GET /api/v1/system/status`. Timestamps stay
 * wire-format strings; the UI parses them with :arr-core ArrTime.
 * [AuthenticationType] and [UpdateMechanism] come from the settings group's
 * HostConfigResource, which owns those shared enums.
 */
@Serializable
data class SystemResource(
    val appName: String? = null,
    val instanceName: String? = null,
    val version: String? = null,
    val buildTime: String? = null,
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
    val mode: RuntimeMode? = null,
    val branch: String? = null,
    val databaseType: DatabaseType? = null,
    val databaseVersion: String? = null,
    val authentication: AuthenticationType? = null,
    val migrationVersion: Int = 0,
    val urlBase: String? = null,
    val runtimeVersion: String? = null,
    val runtimeName: String? = null,
    val startTime: String? = null,
    val packageVersion: String? = null,
    val packageAuthor: String? = null,
    val packageUpdateMechanism: UpdateMechanism? = null,
    val packageUpdateMechanismMessage: String? = null,
)
