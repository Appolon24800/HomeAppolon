package dev.appolon.homelab.arr.feature.sonarr.settings.quality

import dev.appolon.homelab.arr.api.sonarr.CustomFormatApi
import dev.appolon.homelab.arr.api.sonarr.DelayProfileApi
import dev.appolon.homelab.arr.api.sonarr.LanguageApi
import dev.appolon.homelab.arr.api.sonarr.LanguageProfileApi
import dev.appolon.homelab.arr.api.sonarr.LanguageProfileSchemaApi
import dev.appolon.homelab.arr.api.sonarr.QualityDefinitionApi
import dev.appolon.homelab.arr.api.sonarr.QualityProfileApi
import dev.appolon.homelab.arr.api.sonarr.QualityProfileSchemaApi
import dev.appolon.homelab.arr.api.sonarr.ReleaseProfileApi
import dev.appolon.homelab.arr.api.sonarr.SonarrCalendarApis
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
 * Everything the quality and profiles screens need to talk to one Sonarr
 * server, built on the committed client stack. Mirrors the media
 * management connection: one Retrofit over the shared per-server OkHttp
 * client, with the calendar API factories supplying every interface this
 * group uses.
 */
class SonarrSettingsQualityConnection(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {

    /** The per-server API bundle; null when the server or its secrets are gone. */
    suspend fun connect(serverId: ServerId): SonarrQualityApis? {
        val server = registry.byId(serverId) ?: return null
        val endpoint = endpointOf(server) ?: return null
        val retrofit = Retrofit.Builder()
            .baseUrl(ServerEndpoint.normalizeBaseUrl(server.baseUrl))
            .client(clientFactory.clientFor(endpoint))
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        return SonarrQualityApis(
            qualityDefinitionApi = SonarrCalendarApis.qualityDefinition(retrofit),
            qualityProfileApi = SonarrCalendarApis.qualityProfile(retrofit),
            qualityProfileSchemaApi = SonarrCalendarApis.qualityProfileSchema(retrofit),
            languageProfileApi = SonarrCalendarApis.languageProfile(retrofit),
            languageProfileSchemaApi = SonarrCalendarApis.languageProfileSchema(retrofit),
            customFormatApi = SonarrCalendarApis.customFormat(retrofit),
            delayProfileApi = SonarrCalendarApis.delayProfile(retrofit),
            releaseProfileApi = SonarrCalendarApis.releaseProfile(retrofit),
            languageApi = SonarrCalendarApis.language(retrofit),
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
 * schema, language profiles with their schema (the Sonarr-only table),
 * custom formats, delay profiles, release profiles, and the read-only
 * language table.
 */
class SonarrQualityApis(
    val qualityDefinitionApi: QualityDefinitionApi,
    val qualityProfileApi: QualityProfileApi,
    val qualityProfileSchemaApi: QualityProfileSchemaApi,
    val languageProfileApi: LanguageProfileApi,
    val languageProfileSchemaApi: LanguageProfileSchemaApi,
    val customFormatApi: CustomFormatApi,
    val delayProfileApi: DelayProfileApi,
    val releaseProfileApi: ReleaseProfileApi,
    val languageApi: LanguageApi,
)
