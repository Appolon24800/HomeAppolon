package dev.appolon.homelab.arr.feature.lidarr.settings.quality

import dev.appolon.homelab.arr.api.lidarr.CustomFormatApi
import dev.appolon.homelab.arr.api.lidarr.DelayProfileApi
import dev.appolon.homelab.arr.api.lidarr.LanguageApi
import dev.appolon.homelab.arr.api.lidarr.LidarrCalendarApis
import dev.appolon.homelab.arr.api.lidarr.MetadataProfileApi
import dev.appolon.homelab.arr.api.lidarr.MetadataProfileSchemaApi
import dev.appolon.homelab.arr.api.lidarr.MetadataProviderConfigApi
import dev.appolon.homelab.arr.api.lidarr.QualityDefinitionApi
import dev.appolon.homelab.arr.api.lidarr.QualityProfileApi
import dev.appolon.homelab.arr.api.lidarr.QualityProfileSchemaApi
import dev.appolon.homelab.arr.api.lidarr.ReleaseProfileApi
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
 * Everything the quality and profiles screens need to talk to one Lidarr
 * server, built on the committed client stack. Mirrors the media
 * management connection: one Retrofit over the shared per-server OkHttp
 * client, with the calendar API factories supplying every interface this
 * group uses.
 */
class LidarrSettingsQualityConnection(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {

    /** The per-server API bundle; null when the server or its secrets are gone. */
    suspend fun connect(serverId: ServerId): LidarrQualityApis? {
        val server = registry.byId(serverId) ?: return null
        val endpoint = endpointOf(server) ?: return null
        val retrofit = Retrofit.Builder()
            .baseUrl(ServerEndpoint.normalizeBaseUrl(server.baseUrl))
            .client(clientFactory.clientFor(endpoint))
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        return LidarrQualityApis(
            qualityDefinitionApi = LidarrCalendarApis.qualityDefinition(retrofit),
            qualityProfileApi = LidarrCalendarApis.qualityProfile(retrofit),
            qualityProfileSchemaApi = LidarrCalendarApis.qualityProfileSchema(retrofit),
            metadataProfileApi = LidarrCalendarApis.metadataProfile(retrofit),
            metadataProfileSchemaApi = LidarrCalendarApis.metadataProfileSchema(retrofit),
            metadataProviderConfigApi = LidarrCalendarApis.metadataProviderConfig(retrofit),
            customFormatApi = LidarrCalendarApis.customFormat(retrofit),
            delayProfileApi = LidarrCalendarApis.delayProfile(retrofit),
            releaseProfileApi = LidarrCalendarApis.releaseProfile(retrofit),
            languageApi = LidarrCalendarApis.language(retrofit),
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
 * definition table, quality profiles and the blank profile schema, metadata
 * profiles with their schema plus the metadata provider config (the
 * Lidarr-only tables), custom formats, delay profiles, release profiles,
 * and the read-only language table.
 */
class LidarrQualityApis(
    val qualityDefinitionApi: QualityDefinitionApi,
    val qualityProfileApi: QualityProfileApi,
    val qualityProfileSchemaApi: QualityProfileSchemaApi,
    val metadataProfileApi: MetadataProfileApi,
    val metadataProfileSchemaApi: MetadataProfileSchemaApi,
    val metadataProviderConfigApi: MetadataProviderConfigApi,
    val customFormatApi: CustomFormatApi,
    val delayProfileApi: DelayProfileApi,
    val releaseProfileApi: ReleaseProfileApi,
    val languageApi: LanguageApi,
)
