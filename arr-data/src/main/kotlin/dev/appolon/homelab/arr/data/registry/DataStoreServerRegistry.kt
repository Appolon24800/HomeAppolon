package dev.appolon.homelab.arr.data.registry

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataJson
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.data.ServerRegistry
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * [ServerRegistry] over preference-style storage: one JSON blob per server
 * under "arr_server_v1_<id>". The versioned prefix keeps future migrations
 * additive (a v2 reader can coexist with v1 rows).
 *
 * Secret fields (API key, basic auth password, proxy password) are encrypted
 * with [secrets] before anything is written. Values that already carry an
 * envelope marker pass through untouched, so read-modify-write cycles never
 * double-encrypt.
 *
 * Read asymmetry: API key and basic auth password are handed back as stored
 * (envelopes; decrypting is the caller's job), while the proxy password is
 * decrypted by the registry before it is returned. Endpoint construction
 * passes `proxy` through verbatim, so only a decrypted password is usable
 * there; an envelope that no longer opens (Keystore reset) drops just the
 * password and lets that proxy fail authentication instead of taking every
 * stored server offline.
 *
 * Upsert semantics: a blank [ArrServer.id] means "new server" and gets a UUID,
 * a creation timestamp, and the next display order. On update, zero-valued
 * order/createdAtMillis keep the stored values, so a caller that copies a
 * fresh default model cannot accidentally reset them. Explicit non-zero
 * values always win.
 */
class DataStoreServerRegistry internal constructor(
    private val store: PreferencesStore,
    private val secrets: ArrSecrets,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) : ServerRegistry {

    constructor(dataStore: DataStore<Preferences>, secrets: ArrSecrets) :
        this(DataStorePreferencesStore(dataStore), secrets)

    override suspend fun servers(): List<ArrServer> = readAll()

    override suspend fun serversOfType(type: AppType): List<ArrServer> =
        readAll().filter { it.type == type }

    override suspend fun byId(id: ServerId): ArrServer? =
        store.string(key(id))?.let(::decode)

    override suspend fun upsert(server: ArrServer): ArrServer {
        val existing = if (server.id.value.isBlank()) null else byId(server.id)
        val stored = server.withEncryptedSecrets().let { sealed ->
            when (existing) {
                null -> sealed.copy(
                    id = ServerId(server.id.value.ifBlank { idGenerator() }),
                    createdAtMillis = server.createdAtMillis.takeIf { it > 0 } ?: clock(),
                    order = server.order.takeIf { it > 0 } ?: nextOrder(),
                )
                else -> sealed.copy(
                    createdAtMillis = server.createdAtMillis.takeIf { it > 0 }
                        ?: existing.createdAtMillis,
                    order = server.order.takeIf { it > 0 } ?: existing.order,
                )
            }
        }
        store.putString(
            key(stored.id),
            ArrDataJson.encodeToString(ArrServer.serializer(), stored.withEncryptedProxyPassword()),
        )
        return stored
    }

    override suspend fun delete(id: ServerId) {
        store.remove(key(id))
    }

    /**
     * Rewrites display order: listed ids get positions 1..n in the given
     * order; unlisted servers keep their relative order after them. Unknown
     * ids are ignored so a stale list cannot drop stored servers.
     */
    override suspend fun reorder(idsInOrder: List<ServerId>) {
        val current = readAll().associateBy { it.id }
        val listedIds = idsInOrder.distinct()
        val listed = listedIds.mapNotNull { current[it] }
        val rest = current.values.filter { it.id !in listedIds.toSet() }
        (listed + rest).forEachIndexed { index, server ->
            if (server.order != index + 1) {
                store.putString(
                    key(server.id),
                    ArrDataJson.encodeToString(
                        ArrServer.serializer(),
                        server.copy(order = index + 1),
                    ),
                )
            }
        }
    }

    override fun observe(): Flow<List<ArrServer>> =
        store.changes()
            .onStart { emit(Unit) }
            .map { readAll() }
            .distinctUntilChanged()

    private suspend fun readAll(): List<ArrServer> =
        store.keys()
            .filter { it.startsWith(KEY_PREFIX) }
            .mapNotNull { storedKey -> store.string(storedKey)?.let(::decode) }
            .sortedWith(compareBy({ it.order }, { it.createdAtMillis }, { it.id.value }))

    private suspend fun nextOrder(): Int = (readAll().maxOfOrNull { it.order } ?: 0) + 1

    /**
     * Only encrypts values that are not already envelopes. Real arr API keys
     * are 32-character hex strings, so a legitimate key colliding with the
     * "v1:" marker is not a realistic case.
     */
    private fun ArrServer.withEncryptedSecrets(): ArrServer = copy(
        apiKeyEncrypted = apiKeyEncrypted.let { value ->
            if (ArrSecrets.isEncrypted(value)) value else secrets.encrypt(value)
        },
        basicAuthPasswordEncrypted = basicAuthPasswordEncrypted?.let { value ->
            if (ArrSecrets.isEncrypted(value)) value else secrets.encrypt(value)
        },
    )

    private fun key(id: ServerId): String = KEY_PREFIX + id.value

    private fun decode(json: String): ArrServer =
        ArrDataJson.decodeFromString(ArrServer.serializer(), json).withDecryptedProxyPassword()

    /**
     * Encrypts the proxy password at the persistence boundary so no proxy
     * credential rests in storage as plaintext. Envelope markers pass
     * through, so rewriting a row never double-encrypts.
     */
    private fun ArrServer.withEncryptedProxyPassword(): ArrServer =
        withProxyPassword { password ->
            if (ArrSecrets.isEncrypted(password)) password else secrets.encrypt(password)
        }

    /** See the class doc: read paths hand back a usable proxy password. */
    private fun ArrServer.withDecryptedProxyPassword(): ArrServer =
        withProxyPassword { password ->
            if (!ArrSecrets.isEncrypted(password)) password
            else runCatching { secrets.decrypt(password) }.getOrNull()
        }

    private inline fun ArrServer.withProxyPassword(transform: (String) -> String?): ArrServer {
        val config = proxy ?: return this
        val password = config.password ?: return this
        val next = transform(password)
        return copy(proxy = config.copy(password = next))
    }

    private companion object {
        const val KEY_PREFIX = "arr_server_v1_"
    }
}
