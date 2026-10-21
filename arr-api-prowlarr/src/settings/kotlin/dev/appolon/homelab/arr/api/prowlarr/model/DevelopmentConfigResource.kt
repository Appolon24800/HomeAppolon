package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.Serializable

/**
 * Settings singleton behind the DevelopmentConfig tag: verbose logging
 * switches used while debugging a server. The server always serves the row
 * with id 1.
 */
@Serializable
data class DevelopmentConfigResource(
    val id: Int = 1,
    val consoleLogLevel: String? = null,
    val logSql: Boolean = false,
    val logIndexerResponse: Boolean = false,
    val logRotate: Int = 0,
    val filterSentryEvents: Boolean = false,
)
