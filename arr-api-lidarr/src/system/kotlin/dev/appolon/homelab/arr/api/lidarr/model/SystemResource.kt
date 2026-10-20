package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/**
 * Lidarr SystemResource wire resource from /api/v1/system/status. BuildTime and
 * StartTime stay unparsed date-time strings for :arr-core ArrTime consumers.
 * AuthenticationType and UpdateMechanism are shared with the host config DTOs.
 */
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
    val mode: RuntimeMode = RuntimeMode.CONSOLE,
    val branch: String? = null,
    val databaseType: DatabaseType = DatabaseType.SQ_LITE,
    val databaseVersion: String? = null,
    val authentication: AuthenticationType = AuthenticationType.NONE,
    val migrationVersion: Int = 0,
    val urlBase: String? = null,
    val runtimeVersion: String? = null,
    val runtimeName: String? = null,
    val startTime: String = "",
    val packageVersion: String? = null,
    val packageAuthor: String? = null,
    val packageUpdateMechanism: UpdateMechanism = UpdateMechanism.BUILT_IN,
    val packageUpdateMechanismMessage: String? = null,
)
