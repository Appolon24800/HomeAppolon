package dev.appolon.homelab.arr.feature.radarr.settings.quality

import dev.appolon.homelab.arr.api.radarr.CustomFormatApi
import dev.appolon.homelab.arr.api.radarr.DelayProfileApi
import dev.appolon.homelab.arr.api.radarr.LanguageApi
import dev.appolon.homelab.arr.api.radarr.QualityDefinitionApi
import dev.appolon.homelab.arr.api.radarr.QualityProfileApi
import dev.appolon.homelab.arr.api.radarr.QualityProfileSchemaApi
import dev.appolon.homelab.arr.api.radarr.RadarrCalendarApis
import dev.appolon.homelab.arr.api.radarr.ReleaseProfileApi
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.data.ServerRegistry
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import dev.appolon.homelab.arr.net.ArrClientFactory
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.net.DefaultArrClientFactory
import dev.appolon.homelab.arr.net.ServerEndpoint
import dev.appolon.homelab.arr.net.TrustSpec
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Everything the quality and profiles screens need to talk to one Radarr
 * server, built on the committed client stack. Mirrors the media management
 * connection: one Retrofit over the shared per-server OkHttp client, with
 * the calendar API factories supplying every interface this group uses.
 */
class RadarrSettingsQualityConnection(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {

    /** The per-server API bundle; null when the server or its secrets are gone. */
    suspend fun connect(serverId: ServerId): RadarrQualityApis? {
        val server = registry.byId(serverId) ?: return null
        val endpoint = endpointOf(server) ?: return null
        val retrofit = Retrofit.Builder()
            .baseUrl(ServerEndpoint.normalizeBaseUrl(server.baseUrl))
            .client(clientFactory.clientFor(endpoint))
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        return RadarrQualityApis(
            qualityDefinitionApi = RadarrCalendarApis.qualityDefinition(retrofit),
            qualityProfileApi = RadarrCalendarApis.qualityProfile(retrofit),
            qualityProfileSchemaApi = RadarrCalendarApis.qualityProfileSchema(retrofit),
            customFormatApi = RadarrCalendarApis.customFormat(retrofit),
            delayProfileApi = RadarrCalendarApis.delayProfile(retrofit),
            releaseProfileApi = RadarrCalendarApis.releaseProfile(retrofit),
            languageApi = RadarrCalendarApis.language(retrofit),
        )
    }

    private fun endpointOf(server: ArrServer): ServerEndpoint? = try {
        ServerEndpoint(
            serverId = server.id,
            baseUrl = server.baseUrl,
            apiKey = secrets.decrypt(server.apiKeyEncrypted),
            basicAuthUser = server.basicAuthUser,
            basicAuthPassword = server.basicAuthPasswordEncrypted?.let { secrets.decrypt(it) },
            proxy = server.proxy,
            trust = server.trust ?: TrustSpec.System,
        )
    } catch (t: Throwable) {
        null
    }
}

/**
 * Per-server API bundle handed to the repository of this group: the quality
 * definition table with its limits, quality profiles and the blank profile
 * schema, custom formats, delay profiles, release profiles, and the
 * read-only language table.
 */
class RadarrQualityApis(
    val qualityDefinitionApi: QualityDefinitionApi,
    val qualityProfileApi: QualityProfileApi,
    val qualityProfileSchemaApi: QualityProfileSchemaApi,
    val customFormatApi: CustomFormatApi,
    val delayProfileApi: DelayProfileApi,
    val releaseProfileApi: ReleaseProfileApi,
    val languageApi: LanguageApi,
)
