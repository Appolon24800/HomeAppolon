package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
enum class MonitorTypes {
    @SerialName("unknown") UNKNOWN,
    @SerialName("all") ALL,
    @SerialName("future") FUTURE,
    @SerialName("missing") MISSING,
    @SerialName("existing") EXISTING,
    @SerialName("firstSeason") FIRSTSEASON,
    @SerialName("lastSeason") LASTSEASON,
    @SerialName("latestSeason") LATESTSEASON,
    @SerialName("pilot") PILOT,
    @SerialName("recent") RECENT,
    @SerialName("monitorSpecials") MONITORSPECIALS,
    @SerialName("unmonitorSpecials") UNMONITORSPECIALS,
    @SerialName("none") NONE,
    @SerialName("skip") SKIP
}
