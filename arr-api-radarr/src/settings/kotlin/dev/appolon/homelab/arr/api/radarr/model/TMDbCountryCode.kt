package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Radarr TMDbCountryCode wire enum, constants in spec order. */
@Serializable
enum class TMDbCountryCode {
    @SerialName("au") AU,
    @SerialName("br") BR,
    @SerialName("ca") CA,
    @SerialName("fr") FR,
    @SerialName("de") DE,
    @SerialName("gb") GB,
    @SerialName("in") IN,
    @SerialName("ie") IE,
    @SerialName("it") IT,
    @SerialName("nz") NZ,
    @SerialName("ro") RO,
    @SerialName("es") ES,
    @SerialName("us") US,
    ;

    override fun toString(): String = when (this) {
        AU -> "au"
        BR -> "br"
        CA -> "ca"
        FR -> "fr"
        DE -> "de"
        GB -> "gb"
        IN -> "in"
        IE -> "ie"
        IT -> "it"
        NZ -> "nz"
        RO -> "ro"
        ES -> "es"
        US -> "us"
    }
}
