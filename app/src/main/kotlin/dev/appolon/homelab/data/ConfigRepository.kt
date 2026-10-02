package dev.appolon.homelab.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Shared HTTP client. */
object Network {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}

private val Context.dataStore by preferencesDataStore("homelab")

/**
 * Loads the Homer config: cached copy first (instant paint), then a fresh
 * fetch from the live instance which replaces the cache on success.
 */
class ConfigRepository(
    private val context: Context,
    private val client: OkHttpClient = Network.client,
) {
    private val cacheKey = stringPreferencesKey("config_yaml")
    private val themeKey = stringPreferencesKey("theme_source")
    private val pocketIdBaseUrlKey = stringPreferencesKey("pocketid_base_url")
    private val favoritesKey = stringSetPreferencesKey("favorite_services")

    suspend fun loadFavorites(): Set<String> =
        context.dataStore.data.first()[favoritesKey].orEmpty().toSet()

    suspend fun toggleFavorite(id: String): Set<String> {
        val saved = context.dataStore.edit { prefs ->
            prefs[favoritesKey] = Favorites.toggle(prefs[favoritesKey].orEmpty(), id)
        }
        return saved[favoritesKey].orEmpty().toSet()
    }

    /** User-editable PocketID base URL; null until changed in the UI. */
    suspend fun loadPocketIdBaseUrl(): String? =
        context.dataStore.data.first()[pocketIdBaseUrlKey]

    /** Persisted theme choice: "homer" (YAML colours) or "system" (Material You). */
    suspend fun loadThemeSource(): String? =
        context.dataStore.data.first()[themeKey]

    suspend fun saveThemeSource(value: String) {
        context.dataStore.edit { prefs -> prefs[themeKey] = value }
    }

    /** Last successfully fetched YAML, parsed; null when nothing is cached. */
    suspend fun loadCached(): HomerConfig? = withContext(Dispatchers.IO) {
        runCatching {
            val text = readCache()
            text?.let { HomerConfigParser.parse(it) }
        }.getOrNull()
    }

    /** Fresh fetch; caches on success. Fails via Result when offline or unparsable. */
    suspend fun fetch(): Result<HomerConfig> = withContext(Dispatchers.IO) {
        runCatching {
            val body = client.newCall(Request.Builder().url(Homer.CONFIG_URL).build())
                .execute()
                .use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code} for ${Homer.CONFIG_URL}" }
                    response.body.string()
                }
            val config = HomerConfigParser.parse(body)
            writeCache(body)
            config
        }
    }

    private suspend fun readCache(): String? = context.dataStore.data.first()[cacheKey]

    private suspend fun writeCache(text: String) {
        context.dataStore.edit { prefs -> prefs[cacheKey] = text }
    }
}
