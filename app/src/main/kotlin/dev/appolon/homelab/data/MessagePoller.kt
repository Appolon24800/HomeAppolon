package dev.appolon.homelab.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

data class MessageInfo(val title: String?, val content: String?)

/** Extracts title/content from the message endpoint JSON using the YAML mapping. */
object MessageMapper {

    fun map(body: String, mapping: MessageMapping): MessageInfo? {
        val obj = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val title = obj[mapping.title]?.let { (it as? JsonPrimitive)?.contentOrNull }
        val content = obj[mapping.content]?.let { (it as? JsonPrimitive)?.contentOrNull }
        if (title == null && content == null) return null
        return MessageInfo(title, content)
    }
}

/** Fetches the current banner message. Polling itself is driven by the view model. */
class MessagePoller(private val client: OkHttpClient = Network.client) {

    suspend fun fetch(config: MessageConfig): MessageInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(config.url).build()
            client.newCall(request).execute().use { response: Response ->
                if (!response.isSuccessful) return@use null
                MessageMapper.map(response.body.string(), config.mapping)
            }
        }.getOrNull()
    }
}
