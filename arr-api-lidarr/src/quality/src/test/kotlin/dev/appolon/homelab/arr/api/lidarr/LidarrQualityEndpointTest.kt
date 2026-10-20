package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.CustomFormatBulkResource
import dev.appolon.homelab.arr.api.lidarr.model.CustomFormatResource
import dev.appolon.homelab.arr.api.lidarr.model.DelayProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.lidarr.model.LanguageResource
import dev.appolon.homelab.arr.api.lidarr.model.MetadataProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.MetadataProviderConfigResource
import dev.appolon.homelab.arr.api.lidarr.model.PrimaryAlbumType
import dev.appolon.homelab.arr.api.lidarr.model.ProfilePrimaryAlbumTypeItemResource
import dev.appolon.homelab.arr.api.lidarr.model.ProfileReleaseStatusItemResource
import dev.appolon.homelab.arr.api.lidarr.model.Quality
import dev.appolon.homelab.arr.api.lidarr.model.QualityDefinitionResource
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileQualityItemResource
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.ReleaseProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.ReleaseStatus
import dev.appolon.homelab.arr.api.lidarr.model.WriteAudioTagsType
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

internal fun qualityFixture(path: String): String = checkNotNull(
    LidarrQualityEndpointTest::class.java.getResourceAsStream("/fixtures/lidarr/$path.json")
).bufferedReader().use { it.readText() }

internal inline fun <reified T> qualityDto(path: String): T =
    ArrJson.decodeFromString(qualityFixture(path))

internal fun qualityRetrofit(server: MockWebServer): Retrofit = Retrofit.Builder()
    .baseUrl(server.url("/lidarr/"))
    .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
    .build()

/** Each row is one spec operation; body holds the encoded request resource. */
data class QualityEndpointCase(
    val name: String,
    val method: String,
    val path: String,
    val response: String = "",
    val body: JsonElement? = null,
    val queries: Map<String, List<String>> = emptyMap(),
    val call: suspend (Retrofit) -> Any?,
) {
    override fun toString(): String = name
}

@RunWith(Parameterized::class)
class LidarrQualityEndpointTest(private val case: QualityEndpointCase) {
    private val server = MockWebServer()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun happyPath() = runTest {
        server.enqueue(MockResponse().setBody(case.response))
        case.call(qualityRetrofit(server))
        val request = server.takeRequest()
        assertEquals(case.method, request.method)
        assertEquals("/lidarr/${case.path}", request.requestUrl!!.encodedPath)
        assertEquals(case.queries.keys, request.requestUrl!!.queryParameterNames)
        case.queries.forEach { (name, values) ->
            assertEquals(values, request.requestUrl!!.queryParameterValues(name))
        }
        if (case.body != null) {
            assertEquals(case.body, ArrJson.parseToJsonElement(request.body.readUtf8()))
            assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
        } else {
            assertEquals(0L, request.bodySize)
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun operations(): List<Array<Any>> {
            fun query(vararg pairs: Pair<String, String>): Map<String, List<String>> =
                pairs.groupBy({ it.first }, { it.second })
            val quality = Quality(9, "FLAC")
            val definitionBody = QualityDefinitionResource(
                id = 1, quality = Quality(1, "MP3-96"), title = "MP3-96", weight = 1,
                minSize = 0.0, maxSize = 200.0, preferredSize = 75.0,
            )
            val definitionsBody = listOf(
                QualityDefinitionResource(id = 1, quality = Quality(1, "MP3-96"), weight = 1),
                QualityDefinitionResource(id = 9, quality = quality, weight = 9),
            )
            val profileBody = QualityProfileResource(
                name = "Created Profile", upgradeAllowed = true, cutoff = 9,
                items = listOf(
                    QualityProfileQualityItemResource(1, "FLAC", quality, allowed = true),
                ),
            )
            val renamedProfile = QualityProfileResource(
                id = 1, name = "Renamed Profile", upgradeAllowed = false, cutoff = 9,
                items = listOf(
                    QualityProfileQualityItemResource(1, "FLAC", quality, allowed = true),
                ),
            )
            val metadataBody = MetadataProfileResource(
                name = "Created Profile",
                primaryAlbumTypes = listOf(
                    ProfilePrimaryAlbumTypeItemResource(1, PrimaryAlbumType(0, "Album"), true),
                ),
                releaseStatuses = listOf(
                    ProfileReleaseStatusItemResource(1, ReleaseStatus(1, "Official"), true),
                ),
            )
            val renamedMetadata = MetadataProfileResource(
                id = 1, name = "Renamed Profile",
                primaryAlbumTypes = listOf(
                    ProfilePrimaryAlbumTypeItemResource(
                        1, PrimaryAlbumType(0, "Album"), allowed = false,
                    ),
                ),
            )
            val providerBody = MetadataProviderConfigResource(
                id = 1, metadataSource = "Lidarr", writeAudioTags = WriteAudioTagsType.SYNC,
                scrubAudioTags = true, embedCoverArt = false,
            )
            val releaseBody = ReleaseProfileResource(
                enabled = true, required = listOf("flac", "24bit"), ignored = listOf("transcode"),
                indexerId = 2, tags = listOf(1, 2),
            )
            val delayBody = DelayProfileResource(
                enableUsenet = true, enableTorrent = true,
                preferredProtocol = DownloadProtocol.USENET,
                usenetDelay = 120, torrentDelay = 120, tags = emptyList(),
            )
            val renamedDelay = DelayProfileResource(
                id = 1, enableUsenet = true, enableTorrent = true,
                preferredProtocol = DownloadProtocol.USENET, usenetDelay = 30, torrentDelay = 60,
                bypassIfHighestQuality = true, tags = listOf(1, 2),
            )
            val formatBody = CustomFormatResource(name = "Created Format")
            val renamedFormat = CustomFormatResource(
                id = 1, name = "Lossless", includeCustomFormatWhenRenaming = true,
            )
            val formatBulk = CustomFormatBulkResource(
                ids = listOf(1, 2), includeCustomFormatWhenRenaming = true,
            )
            val cases = listOf(
                QualityEndpointCase(
                    "updateQualityDefinition", "PUT", "api/v1/qualitydefinition/1",
                    qualityFixture("qualitydefinition/updateQualityDefinition"),
                    body = ArrJson.encodeToJsonElement(definitionBody),
                ) { r ->
                    assertEquals(
                        qualityDto<QualityDefinitionResource>(
                            "qualitydefinition/updateQualityDefinition",
                        ),
                        LidarrCalendarApis.qualityDefinition(r)
                            .updateQualityDefinition("1", definitionBody),
                    )
                },
                QualityEndpointCase(
                    "getQualityDefinitionById", "GET", "api/v1/qualitydefinition/9",
                    qualityFixture("qualitydefinition/getQualityDefinitionById"),
                ) { r ->
                    assertEquals(
                        qualityDto<QualityDefinitionResource>(
                            "qualitydefinition/getQualityDefinitionById",
                        ),
                        LidarrCalendarApis.qualityDefinition(r).getQualityDefinitionById(9),
                    )
                },
                QualityEndpointCase(
                    "getQualityDefinitions", "GET", "api/v1/qualitydefinition",
                    qualityFixture("qualitydefinition/getQualityDefinitions"),
                ) { r ->
                    assertEquals(
                        qualityDto<List<QualityDefinitionResource>>(
                            "qualitydefinition/getQualityDefinitions",
                        ),
                        LidarrCalendarApis.qualityDefinition(r).getQualityDefinitions(),
                    )
                },
                QualityEndpointCase(
                    "updateQualityDefinitions", "PUT", "api/v1/qualitydefinition/update",
                    body = ArrJson.encodeToJsonElement(definitionsBody),
                ) { r ->
                    LidarrCalendarApis.qualityDefinition(r)
                        .updateQualityDefinitions(definitionsBody)
                },
                QualityEndpointCase(
                    "createQualityProfile", "POST", "api/v1/qualityprofile",
                    qualityFixture("qualityprofile/createQualityProfile"),
                    body = ArrJson.encodeToJsonElement(profileBody),
                ) { r ->
                    assertEquals(
                        qualityDto<QualityProfileResource>("qualityprofile/createQualityProfile"),
                        LidarrCalendarApis.qualityProfile(r).createQualityProfile(profileBody),
                    )
                },
                QualityEndpointCase(
                    "getQualityProfiles", "GET", "api/v1/qualityprofile",
                    qualityFixture("qualityprofile/getQualityProfiles"),
                ) { r ->
                    assertEquals(
                        qualityDto<List<QualityProfileResource>>(
                            "qualityprofile/getQualityProfiles",
                        ),
                        LidarrCalendarApis.qualityProfile(r).getQualityProfiles(),
                    )
                },
                QualityEndpointCase(
                    "deleteQualityProfile", "DELETE", "api/v1/qualityprofile/3",
                ) { r -> LidarrCalendarApis.qualityProfile(r).deleteQualityProfile(3) },
                QualityEndpointCase(
                    "updateQualityProfile", "PUT", "api/v1/qualityprofile/1",
                    qualityFixture("qualityprofile/updateQualityProfile"),
                    body = ArrJson.encodeToJsonElement(renamedProfile),
                ) { r ->
                    assertEquals(
                        qualityDto<QualityProfileResource>("qualityprofile/updateQualityProfile"),
                        LidarrCalendarApis.qualityProfile(r)
                            .updateQualityProfile("1", renamedProfile),
                    )
                },
                QualityEndpointCase(
                    "getQualityProfileById", "GET", "api/v1/qualityprofile/1",
                    qualityFixture("qualityprofile/getQualityProfileById"),
                ) { r ->
                    assertEquals(
                        qualityDto<QualityProfileResource>("qualityprofile/getQualityProfileById"),
                        LidarrCalendarApis.qualityProfile(r).getQualityProfileById(1),
                    )
                },
                QualityEndpointCase(
                    "getQualityProfileSchema", "GET", "api/v1/qualityprofile/schema",
                    qualityFixture("qualityprofileschema/getQualityProfileSchema"),
                ) { r ->
                    assertEquals(
                        qualityDto<QualityProfileResource>(
                            "qualityprofileschema/getQualityProfileSchema",
                        ),
                        LidarrCalendarApis.qualityProfileSchema(r).getQualityProfileSchema(),
                    )
                },
                QualityEndpointCase(
                    "createMetadataProfile", "POST", "api/v1/metadataprofile",
                    qualityFixture("metadataprofile/createMetadataProfile"),
                    body = ArrJson.encodeToJsonElement(metadataBody),
                ) { r ->
                    assertEquals(
                        qualityDto<MetadataProfileResource>(
                            "metadataprofile/createMetadataProfile",
                        ),
                        LidarrCalendarApis.metadataProfile(r).createMetadataProfile(metadataBody),
                    )
                },
                QualityEndpointCase(
                    "getMetadataProfiles", "GET", "api/v1/metadataprofile",
                    qualityFixture("metadataprofile/getMetadataProfiles"),
                ) { r ->
                    assertEquals(
                        qualityDto<List<MetadataProfileResource>>(
                            "metadataprofile/getMetadataProfiles",
                        ),
                        LidarrCalendarApis.metadataProfile(r).getMetadataProfiles(),
                    )
                },
                QualityEndpointCase(
                    "deleteMetadataProfile", "DELETE", "api/v1/metadataprofile/4",
                ) { r -> LidarrCalendarApis.metadataProfile(r).deleteMetadataProfile(4) },
                QualityEndpointCase(
                    "updateMetadataProfile", "PUT", "api/v1/metadataprofile/1",
                    qualityFixture("metadataprofile/updateMetadataProfile"),
                    body = ArrJson.encodeToJsonElement(renamedMetadata),
                ) { r ->
                    assertEquals(
                        qualityDto<MetadataProfileResource>(
                            "metadataprofile/updateMetadataProfile",
                        ),
                        LidarrCalendarApis.metadataProfile(r)
                            .updateMetadataProfile("1", renamedMetadata),
                    )
                },
                QualityEndpointCase(
                    "getMetadataProfileById", "GET", "api/v1/metadataprofile/1",
                    qualityFixture("metadataprofile/getMetadataProfileById"),
                ) { r ->
                    assertEquals(
                        qualityDto<MetadataProfileResource>(
                            "metadataprofile/getMetadataProfileById",
                        ),
                        LidarrCalendarApis.metadataProfile(r).getMetadataProfileById(1),
                    )
                },
                QualityEndpointCase(
                    "getMetadataProfileSchema", "GET", "api/v1/metadataprofile/schema",
                    qualityFixture("metadataprofileschema/getMetadataProfileSchema"),
                ) { r ->
                    assertEquals(
                        qualityDto<MetadataProfileResource>(
                            "metadataprofileschema/getMetadataProfileSchema",
                        ),
                        LidarrCalendarApis.metadataProfileSchema(r).getMetadataProfileSchema(),
                    )
                },
                QualityEndpointCase(
                    "getMetadataProviderConfigById", "GET", "api/v1/config/metadataprovider/1",
                    qualityFixture("metadataprovider/getMetadataProviderConfig"),
                ) { r ->
                    assertEquals(
                        qualityDto<MetadataProviderConfigResource>(
                            "metadataprovider/getMetadataProviderConfig",
                        ),
                        LidarrCalendarApis.metadataProviderConfig(r)
                            .getMetadataProviderConfigById(1),
                    )
                },
                QualityEndpointCase(
                    "updateMetadataProviderConfig", "PUT", "api/v1/config/metadataprovider/1",
                    qualityFixture("metadataprovider/getMetadataProviderConfig"),
                    body = ArrJson.encodeToJsonElement(providerBody),
                ) { r ->
                    assertEquals(
                        qualityDto<MetadataProviderConfigResource>(
                            "metadataprovider/getMetadataProviderConfig",
                        ),
                        LidarrCalendarApis.metadataProviderConfig(r)
                            .updateMetadataProviderConfig("1", providerBody),
                    )
                },
                QualityEndpointCase(
                    "getMetadataProviderConfig", "GET", "api/v1/config/metadataprovider",
                    qualityFixture("metadataprovider/getMetadataProviderConfig"),
                ) { r ->
                    assertEquals(
                        qualityDto<MetadataProviderConfigResource>(
                            "metadataprovider/getMetadataProviderConfig",
                        ),
                        LidarrCalendarApis.metadataProviderConfig(r).getMetadataProviderConfig(),
                    )
                },
                QualityEndpointCase(
                    "getReleaseProfileById", "GET", "api/v1/releaseprofile/1",
                    qualityFixture("releaseprofile/getReleaseProfileById"),
                ) { r ->
                    assertEquals(
                        qualityDto<ReleaseProfileResource>("releaseprofile/getReleaseProfileById"),
                        LidarrCalendarApis.releaseProfile(r).getReleaseProfileById(1),
                    )
                },
                QualityEndpointCase(
                    "updateReleaseProfile", "PUT", "api/v1/releaseprofile/1",
                    qualityFixture("releaseprofile/updateReleaseProfile"),
                    body = ArrJson.encodeToJsonElement(
                        ReleaseProfileResource(
                            enabled = false, required = listOf("flac"),
                            ignored = listOf("transcode", "remux"),
                            indexerId = 2, tags = listOf(1),
                        ),
                    ),
                ) { r ->
                    assertEquals(
                        qualityDto<ReleaseProfileResource>("releaseprofile/updateReleaseProfile"),
                        LidarrCalendarApis.releaseProfile(r).updateReleaseProfile(
                            "1",
                            ReleaseProfileResource(
                                enabled = false, required = listOf("flac"),
                                ignored = listOf("transcode", "remux"),
                            indexerId = 2, tags = listOf(1),
                            ),
                        ),
                    )
                },
                QualityEndpointCase(
                    "deleteReleaseProfile", "DELETE", "api/v1/releaseprofile/3",
                ) { r -> LidarrCalendarApis.releaseProfile(r).deleteReleaseProfile(3) },
                QualityEndpointCase(
                    "getReleaseProfiles", "GET", "api/v1/releaseprofile",
                    qualityFixture("releaseprofile/getReleaseProfiles"),
                ) { r ->
                    assertEquals(
                        qualityDto<List<ReleaseProfileResource>>(
                            "releaseprofile/getReleaseProfiles",
                        ),
                        LidarrCalendarApis.releaseProfile(r).getReleaseProfiles(),
                    )
                },
                QualityEndpointCase(
                    "createReleaseProfile", "POST", "api/v1/releaseprofile",
                    qualityFixture("releaseprofile/createReleaseProfile"),
                    body = ArrJson.encodeToJsonElement(releaseBody),
                ) { r ->
                    assertEquals(
                        qualityDto<ReleaseProfileResource>("releaseprofile/createReleaseProfile"),
                        LidarrCalendarApis.releaseProfile(r).createReleaseProfile(releaseBody),
                    )
                },
                QualityEndpointCase(
                    "createDelayProfile", "POST", "api/v1/delayprofile",
                    qualityFixture("delayprofile/createDelayProfile"),
                    body = ArrJson.encodeToJsonElement(delayBody),
                ) { r ->
                    assertEquals(
                        qualityDto<DelayProfileResource>("delayprofile/createDelayProfile"),
                        LidarrCalendarApis.delayProfile(r).createDelayProfile(delayBody),
                    )
                },
                QualityEndpointCase(
                    "getDelayProfiles", "GET", "api/v1/delayprofile",
                    qualityFixture("delayprofile/getDelayProfiles"),
                ) { r ->
                    assertEquals(
                        qualityDto<List<DelayProfileResource>>("delayprofile/getDelayProfiles"),
                        LidarrCalendarApis.delayProfile(r).getDelayProfiles(),
                    )
                },
                QualityEndpointCase(
                    "deleteDelayProfile", "DELETE", "api/v1/delayprofile/1",
                ) { r -> LidarrCalendarApis.delayProfile(r).deleteDelayProfile(1) },
                QualityEndpointCase(
                    "updateDelayProfile", "PUT", "api/v1/delayprofile/1",
                    qualityFixture("delayprofile/updateDelayProfile"),
                    body = ArrJson.encodeToJsonElement(renamedDelay),
                ) { r ->
                    assertEquals(
                        qualityDto<DelayProfileResource>("delayprofile/updateDelayProfile"),
                        LidarrCalendarApis.delayProfile(r).updateDelayProfile("1", renamedDelay),
                    )
                },
                QualityEndpointCase(
                    "getDelayProfileById", "GET", "api/v1/delayprofile/2",
                    qualityFixture("delayprofile/getDelayProfileById"),
                ) { r ->
                    assertEquals(
                        qualityDto<DelayProfileResource>("delayprofile/getDelayProfileById"),
                        LidarrCalendarApis.delayProfile(r).getDelayProfileById(2),
                    )
                },
                QualityEndpointCase(
                    "reorderDelayProfile", "PUT", "api/v1/delayprofile/reorder/4",
                    queries = query("afterId" to "5"),
                ) { r -> LidarrCalendarApis.delayProfile(r).reorderDelayProfile(4, 5) },
                QualityEndpointCase(
                    "getCustomFormatById", "GET", "api/v1/customformat/1",
                    qualityFixture("customformat/getCustomFormatById"),
                ) { r ->
                    assertEquals(
                        qualityDto<CustomFormatResource>("customformat/getCustomFormatById"),
                        LidarrCalendarApis.customFormat(r).getCustomFormatById(1),
                    )
                },
                QualityEndpointCase(
                    "updateCustomFormat", "PUT", "api/v1/customformat/1",
                    qualityFixture("customformat/updateCustomFormat"),
                    body = ArrJson.encodeToJsonElement(renamedFormat),
                ) { r ->
                    assertEquals(
                        qualityDto<CustomFormatResource>("customformat/updateCustomFormat"),
                        LidarrCalendarApis.customFormat(r).updateCustomFormat("1", renamedFormat),
                    )
                },
                QualityEndpointCase(
                    "deleteCustomFormat", "DELETE", "api/v1/customformat/3",
                ) { r -> LidarrCalendarApis.customFormat(r).deleteCustomFormat(3) },
                QualityEndpointCase(
                    "getCustomFormats", "GET", "api/v1/customformat",
                    qualityFixture("customformat/getCustomFormats"),
                ) { r ->
                    assertEquals(
                        qualityDto<List<CustomFormatResource>>("customformat/getCustomFormats"),
                        LidarrCalendarApis.customFormat(r).getCustomFormats(),
                    )
                },
                QualityEndpointCase(
                    "createCustomFormat", "POST", "api/v1/customformat",
                    qualityFixture("customformat/createCustomFormat"),
                    body = ArrJson.encodeToJsonElement(formatBody),
                ) { r ->
                    assertEquals(
                        qualityDto<CustomFormatResource>("customformat/createCustomFormat"),
                        LidarrCalendarApis.customFormat(r).createCustomFormat(formatBody),
                    )
                },
                QualityEndpointCase(
                    "updateCustomFormatBulk", "PUT", "api/v1/customformat/bulk",
                    qualityFixture("customformat/updateCustomFormatBulk"),
                    body = ArrJson.encodeToJsonElement(formatBulk),
                ) { r ->
                    assertEquals(
                        qualityDto<List<CustomFormatResource>>(
                            "customformat/updateCustomFormatBulk",
                        ),
                        LidarrCalendarApis.customFormat(r).updateCustomFormatBulk(formatBulk),
                    )
                },
                QualityEndpointCase(
                    "deleteCustomFormatBulk", "DELETE", "api/v1/customformat/bulk",
                    body = ArrJson.encodeToJsonElement(formatBulk),
                ) { r -> LidarrCalendarApis.customFormat(r).deleteCustomFormatBulk(formatBulk) },
                QualityEndpointCase(
                    "getCustomFormatSchema", "GET", "api/v1/customformat/schema",
                    qualityFixture("customformat/getCustomFormatSchema"),
                ) { r ->
                    assertEquals(
                        ArrJson.parseToJsonElement(
                            qualityFixture("customformat/getCustomFormatSchema"),
                        ),
                        LidarrCalendarApis.customFormat(r).getCustomFormatSchema(),
                    )
                },
                QualityEndpointCase(
                    "getLanguageById", "GET", "api/v1/language/1",
                    qualityFixture("language/getLanguageById"),
                ) { r ->
                    assertEquals(
                        qualityDto<LanguageResource>("language/getLanguageById"),
                        LidarrCalendarApis.language(r).getLanguageById(1),
                    )
                },
                QualityEndpointCase(
                    "getLanguages", "GET", "api/v1/language",
                    qualityFixture("language/getLanguages"),
                ) { r ->
                    assertEquals(
                        qualityDto<List<LanguageResource>>("language/getLanguages"),
                        LidarrCalendarApis.language(r).getLanguages(),
                    )
                },
            )
            return cases.map { arrayOf(it) }
        }
    }
}
