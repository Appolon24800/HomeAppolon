package dev.appolon.homelab.arr.feature.radarr.detail

import dev.appolon.homelab.arr.api.radarr.FakeRadarrCalendar
import dev.appolon.homelab.arr.api.radarr.FakeRadarrQueue
import dev.appolon.homelab.arr.api.radarr.FakeRadarrSystem
import dev.appolon.homelab.arr.api.radarr.fake.FakeRadarrLibrary
import dev.appolon.homelab.arr.api.radarr.model.CommandStatus
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import java.io.IOException

/**
 * In-memory [RadarrDetailConnection] over the module's committed fakes.
 * [networkUp] false models a device offline: no apis, no base URL, and
 * command posts failing with [ArrError.Network].
 */
class FakeRadarrDetailConnection(
    val library: FakeRadarrLibrary = FakeRadarrLibrary(),
    val queue: FakeRadarrQueue = FakeRadarrQueue(),
    val calendar: FakeRadarrCalendar = FakeRadarrCalendar(),
    val system: FakeRadarrSystem = FakeRadarrSystem(),
) : RadarrDetailConnection {

    var networkUp: Boolean = true

    /** Command bodies posted while online, in order. */
    val commandBodies = mutableListOf<JsonObject>()

    override suspend fun apis(serverId: ServerId): RadarrDetailApis? {
        if (!networkUp) return null
        return RadarrDetailApis(
            movie = library.movie,
            movieFile = library.movieFile,
            credit = library.credit,
            extraFile = library.extraFile,
            movieFolder = library.movieFolder,
            history = queue,
            manualImport = queue,
            tag = system,
            qualityProfile = calendar,
        )
    }

    override suspend fun baseUrlOf(serverId: ServerId): String? =
        if (networkUp) BASE_URL else null

    override suspend fun postCommand(
        serverId: ServerId,
        body: JsonObject,
    ): ArrResult<CommandReceipt> {
        if (!networkUp) return ArrResult.err(ArrError.Network(IOException("offline")))
        commandBodies += body
        return ArrResult.ok(CommandReceipt(id = 1, name = null, status = CommandStatus.QUEUED))
    }

    override suspend fun imageLoaderClient(serverId: ServerId): OkHttpClient? = null

    companion object {
        const val BASE_URL = "http://radarr.test/"
    }
}
