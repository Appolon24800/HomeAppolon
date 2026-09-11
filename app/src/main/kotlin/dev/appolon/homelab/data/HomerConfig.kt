package dev.appolon.homelab.data

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.Serializable

/** Endpoints of the Homer instance this app mirrors. */
object Homer {
    const val BASE_URL = "https://home.appolon.dev/"
    const val CONFIG_URL = "https://home.appolon.dev/assets/config.yml"
}

@Serializable
data class HomerConfig(
    val title: String? = null,
    val subtitle: String? = null,
    val services: List<ServiceGroup> = emptyList(),
    val links: List<ServiceLink> = emptyList(),
    val message: MessageConfig? = null,
)

@Serializable
data class ServiceGroup(
    val name: String = "",
    val icon: String? = null,
    val items: List<ServiceItem> = emptyList(),
)

@Serializable
data class ServiceItem(
    val name: String,
    val logo: String? = null,
    val subtitle: String? = null,
    val tag: String? = null,
    val keywords: String? = null,
    val url: String = "",
)

@Serializable
data class ServiceLink(
    val name: String,
    val icon: String? = null,
    val url: String,
    val target: String? = null,
)

@Serializable
data class MessageConfig(
    val url: String,
    val mapping: MessageMapping = MessageMapping(),
    val refreshInterval: Int = 10_000,
)

@Serializable
data class MessageMapping(
    val title: String = "title",
    val content: String = "value",
)

/** Lenient Homer YAML decoding: unknown keys (colors, theme, layout…) are ignored. */
object HomerConfigParser {
    private val yaml = Yaml(configuration = YamlConfiguration(strictMode = false))

    fun parse(text: String): HomerConfig = yaml.decodeFromString(HomerConfig.serializer(), text)
}
